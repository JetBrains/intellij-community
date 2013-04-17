/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectEntityChangeListener;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 4/1/13 1:31 PM
 */
public class ExternalSystemUtil {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemUtil.class.getName());

  @NotNull public static final String PATH_SEPARATOR = "/";

  @NotNull private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(?:.*/)?(.+?)(?:-([\\d+](?:\\.[\\d]+)*))?(?:\\.[^\\.]+?)?");

  @NotNull private static final NotNullLazyValue<Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?>>> MANAGERS =
    new AtomicNotNullLazyValue<Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?>>>() {
      @NotNull
      @Override
      protected Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?>> compute() {
        Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?>> result = ContainerUtilRt.newHashMap();
        for (ExternalSystemManager manager : ExternalSystemManager.EP_NAME.getExtensions()) {
          result.put(manager.getSystemId(), manager);
        }
        return result;
      }
    };

  @NotNull public static final Comparator<Object> ORDER_AWARE_COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      int order1 = getOrder(o1);
      int order2 = getOrder(o2);
      return order1 > order2 ? 1 : order1 < order2 ? -1 : 0;
    }

    private int getOrder(@NotNull Object o) {
      Queue<Class<?>> toCheck = new ArrayDeque<Class<?>>();
      toCheck.add(o.getClass());
      while (!toCheck.isEmpty()) {
        Class<?> clazz = toCheck.poll();
        Order annotation = clazz.getAnnotation(Order.class);
        if (annotation != null) {
          return annotation.value();
        }
        toCheck.add(clazz.getSuperclass());
        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces != null) {
          Collections.addAll(toCheck, interfaces);
        }
      }
      return ExternalSystemConstants.UNORDERED;
    }
  };


  private ExternalSystemUtil() {
  }

  /**
   * @param path    target path
   * @return absolute path that points to the same location as the given one and that uses only slashes
   */
  @NotNull
  public static String toCanonicalPath(@NotNull String path) {
    return PathUtil.getCanonicalPath(new File(path).getAbsolutePath());
  }

  @NotNull
  public static String extractNameFromPath(@NotNull String path) {
    String strippedPath = stripPath(path);
    final int i = strippedPath.lastIndexOf(PATH_SEPARATOR);
    final String result;
    if (i < 0 || i >= strippedPath.length() - 1) {
      result = strippedPath;
    }
    else {
      result = strippedPath.substring(i + 1);
    }
    return result;
  }

  @NotNull
  private static String stripPath(@NotNull String path) {
    String[] endingsToStrip = {"/", "!", ".jar"};
    StringBuilder buffer = new StringBuilder(path);
    for (String ending : endingsToStrip) {
      if (buffer.lastIndexOf(ending) == buffer.length() - ending.length()) {
        buffer.setLength(buffer.length() - ending.length());
      }
    }
    return buffer.toString();
  }

  @NotNull
  public static String getLibraryName(@NotNull Library library) {
    final String result = library.getName();
    if (result != null) {
      return result;
    }
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : library.getUrls(type)) {
        String candidate = extractNameFromPath(url);
        if (!StringUtil.isEmpty(candidate)) {
          return candidate;
        }
      }
    }
    assert false;
    return "unknown-lib";
  }

  @Nullable
  public static ArtifactInfo parseArtifactInfo(@NotNull String fileName) {
    Matcher matcher = ARTIFACT_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      return null;
    }
    return new ArtifactInfo(matcher.group(1), null, matcher.group(2));
  }

  @NotNull
  public static String getLocalFileSystemPath(@NotNull VirtualFile file) {
    if (file.getFileType() == FileTypes.ARCHIVE) {
      final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jar != null) {
        return jar.getPath();
      }
    }
    return file.getPath();
  }

  /**
   * Tries to dispatch given entity via the given visitor.
   *
   * @param entity   intellij project entity candidate to dispatch
   * @param visitor  dispatch callback to use for the given entity
   */
  public static void dispatch(@Nullable Object entity, @NotNull IdeEntityVisitor visitor) {
    if (entity instanceof Project) {
      visitor.visit(((Project)entity));
    }
    else if (entity instanceof Module) {
      visitor.visit(((Module)entity));
    }
    else if (entity instanceof ModuleAwareContentRoot) {
      visitor.visit(((ModuleAwareContentRoot)entity));
    }
    else if (entity instanceof LibraryOrderEntry) {
      visitor.visit(((LibraryOrderEntry)entity));
    }
    else if (entity instanceof ModuleOrderEntry) {
      visitor.visit(((ModuleOrderEntry)entity));
    }
    else if (entity instanceof Library) {
      visitor.visit(((Library)entity));
    }
  }

  @NotNull
  public static ProjectSystemId detectOwner(@Nullable ProjectEntityData externalEntity, @Nullable Object ideEntity) {
    if (ideEntity != null) {
      return ProjectSystemId.IDE;
    }
    else if (externalEntity != null) {
      return externalEntity.getOwner();
    }
    else {
      throw new RuntimeException(String.format(
        "Can't detect owner system for the given arguments: external=%s, ide=%s", externalEntity, ideEntity
      ));
    }
  }

  public static void executeProjectChangeAction(@NotNull Project project,
                                                @NotNull final ProjectSystemId externalSystemId,
                                                @NotNull Object entityToChange,
                                                @NotNull Runnable task)
  {
    executeProjectChangeAction(project, externalSystemId, entityToChange, false, task);
  }

  public static void executeProjectChangeAction(@NotNull Project project,
                                                @NotNull final ProjectSystemId externalSystemId,
                                                @NotNull Object entityToChange,
                                                boolean synchronous,
                                                @NotNull Runnable task)
  {
    executeProjectChangeAction(project, externalSystemId, Collections.singleton(entityToChange), synchronous, task);
  }

  public static void executeProjectChangeAction(@NotNull final Project project,
                                                @NotNull final ProjectSystemId externalSystemId,
                                                @NotNull final Iterable<?> entitiesToChange,
                                                @NotNull final Runnable task)
  {
    executeProjectChangeAction(project, externalSystemId, entitiesToChange, false, task);
  }

  public static void executeProjectChangeAction(@NotNull final Project project,
                                                @NotNull final ProjectSystemId externalSystemId,
                                                @NotNull final Iterable<?> entitiesToChange,
                                                boolean synchronous,
                                                @NotNull final Runnable task)
  {
    Runnable wrappedTask = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final ProjectEntityChangeListener publisher = project.getMessageBus().syncPublisher(ProjectEntityChangeListener.TOPIC);
            for (Object e : entitiesToChange) {
              publisher.onChangeStart(e, externalSystemId);
            }
            try {
              task.run();
            }
            finally {
              for (Object e : entitiesToChange) {
                publisher.onChangeEnd(e, externalSystemId);
              }
            }
          }
        });
      }
    };

    if (synchronous) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        wrappedTask.run();
      }
      else {
        UIUtil.invokeAndWaitIfNeeded(wrappedTask);
      }
    }
    else {
      UIUtil.invokeLaterIfNeeded(wrappedTask);
    }
  }

  @NotNull
  public static String getOutdatedEntityName(@NotNull String entityName, @NotNull String gradleVersion, @NotNull String ideVersion) {
    return String.format("%s (%s -> %s)", entityName, ideVersion, gradleVersion);
  }

  @Nullable
  public static <T> T getToolWindowElement(@NotNull Class<T> clazz,
                                           @Nullable DataContext context,
                                           @NotNull DataKey<T> key,
                                           @NotNull ProjectSystemId externalSystemId)
  {
    // TODO den use external system
    if (context != null) {
      final T result = key.getData(context);
      if (result != null) {
        return result;
      }
    }

    if (context == null) {
      return null;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }

    return getToolWindowElement(clazz, project, key, externalSystemId);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T getToolWindowElement(@NotNull Class<T> clazz,
                                           @NotNull Project project,
                                           @NotNull DataKey<T> key,
                                           @NotNull ProjectSystemId externalSystemId)
  {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) {
      return null;
    }
    // TODO den use external system.
    final ToolWindow toolWindow = null;
//    final ToolWindow toolWindow = toolWindowManager.getToolWindow(GradleConstants.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return null;
    }

    final ContentManager contentManager = toolWindow.getContentManager();
    if (contentManager == null) {
      return null;
    }

    for (Content content : contentManager.getContents()) {
      final JComponent component = content.getComponent();
      if (component instanceof DataProvider) {
        final Object data = ((DataProvider)component).getData(key.getName());
        if (data != null && clazz.isInstance(data)) {
          return (T)data;
        }
      }
    }
    return null;
  }

  @Nullable
  public static ExternalSystemManager<?, ?, ?, ?> getManager(@NotNull ProjectSystemId externalSystemId) {
    return MANAGERS.getValue().get(externalSystemId);
  }

  /**
   * Configures given classpath to reference target i18n bundle file(s).
   *
   * @param classPath     process classpath
   * @param bundlePath    path to the target bundle file
   * @param contextClass  class from the same content root as the target bundle file
   */
  public static void addBundle(@NotNull PathsList classPath, @NotNull String bundlePath, @NotNull Class<?> contextClass) {
    String pathToUse = bundlePath.replace('.', '/');
    if (!pathToUse.endsWith(".properties")) {
      pathToUse += ".properties";
    }
    if (!pathToUse.startsWith("/")) {
      pathToUse = '/' + pathToUse;
    }
    classPath.add(PathManager.getResourceRoot(contextClass, pathToUse));
  }

  /**
   * {@link RemoteUtil#unwrap(Throwable) unwraps} given exception if possible and builds error message for it.
   *
   * @param e  exception to process
   * @return   error message for the given exception
   */
  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
  @NotNull
  public static String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == ExternalSystemException.class) {
      return String.format("exception during working with external system: %s", ((ExternalSystemException)unwrapped).getOriginalReason());
    }
    else {
      StringWriter writer = new StringWriter();
      unwrapped.printStackTrace(new PrintWriter(writer));
      return writer.toString();
    }
  }

  @NotNull
  public static Map<Key<?>, Collection<DataNode<?>>> group(@NotNull Collection<DataNode<?>> nodes) {
    if (nodes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Key<?>, Collection<DataNode<?>>> result = ContainerUtilRt.newHashMap();
    for (DataNode<?> node : nodes) {
      Collection<DataNode<?>> n = result.get(node.getKey());
      if (n == null) {
        result.put(node.getKey(), n = ContainerUtilRt.newArrayList());
      }
      n.add(node);
    }
    return result;
  }

  @NotNull
  public static <K, V> Map<DataNode<K>, Collection<DataNode<V>>> groupBy(@NotNull Collection<DataNode<V>> nodes, @NotNull Key<K> key) {
    Map<DataNode<K>, Collection<DataNode<V>>> result = ContainerUtilRt.newHashMap();
    for (DataNode<V> data : nodes) {
      DataNode<K> grouper = data.getDataNode(key);
      if (grouper == null) {
        LOG.warn(String.format(
          "Skipping entry '%s' during grouping. Reason: it doesn't provide a value for key %s. Given entries: %s",
          data, key, nodes
        ));
        continue;
      }
      Collection<DataNode<V>> grouped = result.get(grouper);
      if (grouped == null) {
        result.put(grouper, grouped = ContainerUtilRt.newArrayList());
      }
      grouped.add(data);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> Collection<DataNode<T>> getChildren(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    Collection<DataNode<T>> result = null;
    for (DataNode<?> child : node.getChildren()) {
      if (!key.equals(child.getKey())) {
        continue;
      }
      if (result == null) {
        result = ContainerUtilRt.newArrayList();
      }
      result.add((DataNode<T>)child);
    }
    return result == null ? Collections.<DataNode<T>>emptyList() : result;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> DataNode<T> find(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    for (DataNode<?> child : node.getChildren()) {
      if (key.equals(child.getKey())) {
        return (DataNode<T>)child;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> DataNode<T> find(@NotNull DataNode<?> node, @NotNull Key<T> key, BooleanFunction<DataNode<T>> predicate) {
    for (DataNode<?> child : node.getChildren()) {
      if (key.equals(child.getKey()) && predicate.fun((DataNode<T>)child)) {
        return (DataNode<T>)child;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> Collection<DataNode<T>> findAll(@NotNull DataNode<?> parent, @NotNull Key<T> key) {
    Collection<DataNode<T>> result = null;
    for (DataNode<?> child : parent.getChildren()) {
      if (!key.equals(child.getKey())) {
        continue;
      }
      if (result == null) {
        result = ContainerUtilRt.newArrayList();
      }
      result.add((DataNode<T>)child);
    }
    return result == null ? Collections.<DataNode<T>>emptyList() : result;
  }

  @NotNull
  public static String toReadableName(@NotNull ProjectSystemId id) {
    return StringUtil.capitalize(id.toString().toLowerCase());
  }

  public static void refreshProject(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    refreshProject(project, externalSystemId, new Ref<String>());
  }

  public static void refreshProject(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @NotNull final Consumer<String> errorCallback)
  {
    final Ref<String> errorMessageHolder = new Ref<String>() {
      @Override
      public void set(@Nullable String value) {
        if (value != null) {
          errorCallback.consume(value);
        }
      }
    };
    refreshProject(project, externalSystemId, errorMessageHolder);
  }

  public static void refreshProject(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @NotNull final Ref<String> errorMessageHolder)
  {
    ExternalSystemSettingsManager settingsManager = ServiceManager.getService(ExternalSystemSettingsManager.class);
    AbstractExternalSystemSettings settings = settingsManager.getSettings(project, externalSystemId);
    final String linkedProjectPath = settings.getLinkedExternalProjectPath();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return;
    }
    assert linkedProjectPath != null;
    Ref<String> errorDetailsHolder = new Ref<String>() {
      @Override
      public void set(@Nullable String error) {
        if (!StringUtil.isEmpty(error)) {
          assert error != null;
          LOG.warn(error);
        }
      }
    };
    refreshProject(project, externalSystemId, linkedProjectPath, errorMessageHolder, errorDetailsHolder, true, false);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  private static String extractDetails(@NotNull Throwable e) {
    final Throwable unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof ExternalSystemException) {
      return ((ExternalSystemException)unwrapped).getOriginalReason();
    }
    return null;
  }

  /**
   * Queries slave gradle process to refresh target gradle project.
   *
   * @param project             target intellij project to use
   * @param externalProjectPath path of the target gradle project's file
   * @param errorMessageHolder  holder for the error message that describes a problem occurred during the refresh (if any)
   * @param errorDetailsHolder  holder for the error details of the problem occurred during the refresh (if any)
   * @param resolveLibraries    flag that identifies whether gradle libraries should be resolved during the refresh
   * @return the most up-to-date gradle project (if any)
   */
  @Nullable
  public static DataNode<ProjectData> refreshProject(@NotNull final Project project,
                                                     @NotNull final ProjectSystemId externalSystemId,
                                                     @NotNull final String externalProjectPath,
                                                     @NotNull final Ref<String> errorMessageHolder,
                                                     @NotNull final Ref<String> errorDetailsHolder,
                                                     final boolean resolveLibraries,
                                                     final boolean modal)
  {
    final Ref<DataNode<ProjectData>> externalProject = new Ref<DataNode<ProjectData>>();
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        ExternalSystemResolveProjectTask task
          = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, resolveLibraries);
        task.execute(indicator);
        externalProject.set(task.getExternalProject());
        final Throwable error = task.getError();
        if (error == null) {
          return;
        }
        final String message = buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          errorMessageHolder.set(String.format(
            "Can't resolve %s project at '%s'. Reason: %s",
            toReadableName(externalSystemId), externalProjectPath, message
          ));
        }
        else {
          errorMessageHolder.set(message);
        }
        errorDetailsHolder.set(extractDetails(error));
      }
    };

    // TODO den uncomment
    //final TaskUnderProgress refreshTasksTask = new TaskUnderProgress() {
    //  @Override
    //  public void execute(@NotNull ProgressIndicator indicator) {
    //    final ExternalSystemRefreshTasksListTask task = new ExternalSystemRefreshTasksListTask(project, externalProjectPath);
    //    task.execute(indicator);
    //  }
    //};

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (modal) {
          String title = ExternalSystemBundle.message("progress.import.text", toReadableName(externalSystemId));
          ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
              // TODO den uncomment
              //setTitle(ExternalSystemBundle.message("gradle.task.progress.initial.text"));
              //refreshTasksTask.execute(indicator);
            }
          });
        }
        else {
          String title = ExternalSystemBundle.message("progress.refresh.text", toReadableName(externalSystemId));
          ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
              // TODO den uncomment
              //setTitle(ExternalSystemBundle.message("gradle.task.progress.initial.text"));
              //refreshTasksTask.execute(indicator);
            }
          });
        }
      }
    });
    return externalProject.get();
  }

  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }

  @Nullable
  public static Sdk findJdk(@NotNull JavaSdkVersion version) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> javaSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    Sdk candidate = null;
    for (Sdk sdk : javaSdks) {
      JavaSdkVersion v = javaSdk.getVersion(sdk);
      if (v == version) {
        return sdk;
      }
      else if (candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel())) {
        candidate = sdk;
      }
    }
    return candidate;
  }

  public static void orderAwareSort(@NotNull List<?> data) {
    Collections.sort(data, ORDER_AWARE_COMPARATOR);
  }
}
