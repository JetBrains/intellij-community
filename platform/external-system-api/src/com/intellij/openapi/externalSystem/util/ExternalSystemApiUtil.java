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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public class ExternalSystemApiUtil {

  private static final Logger LOG                           = Logger.getInstance("#" + ExternalSystemApiUtil.class.getName());
  private static final String LAST_USED_PROJECT_PATH_PREFIX = "LAST_EXTERNAL_PROJECT_PATH_";

  @NotNull public static final String PATH_SEPARATOR = "/";

  @NotNull private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(?:.*/)?(.+?)(?:-([\\d+](?:\\.[\\d]+)*))?(?:\\.[^\\.]+?)?");

  @NotNull private static final NotNullLazyValue<Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?, ?>>> MANAGERS =
    new AtomicNotNullLazyValue<Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?, ?>>>() {
      @NotNull
      @Override
      protected Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?, ?>> compute() {
        Map<ProjectSystemId, ExternalSystemManager<?, ?, ?, ?, ?>> result = ContainerUtilRt.newHashMap();
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

  @NotNull private static final NullableFunction<DataNode<?>, Key<?>> GROUPER = new NullableFunction<DataNode<?>, Key<?>>() {
    @Override
    public Key<?> fun(DataNode<?> node) {
      return node.getKey();
    }
  };

  @NotNull private static final Comparator<Object> COMPARABLE_GLUE = new Comparator<Object>() {
    @SuppressWarnings("unchecked")
    @Override
    public int compare(Object o1, Object o2) {
      return ((Comparable)o1).compareTo(o2);
    }
  };

  private ExternalSystemApiUtil() {
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

  public static void orderAwareSort(@NotNull List<?> data) {
    Collections.sort(data, ORDER_AWARE_COMPARATOR);
  }

  /**
   * @param path    target path
   * @return absolute path that points to the same location as the given one and that uses only slashes
   */
  @NotNull
  public static String toCanonicalPath(@NotNull String path) {
    String p = normalizePath(new File(path).getAbsolutePath());
    assert p != null;
    return PathUtil.getCanonicalPath(p);
  }

  @NotNull
  public static String getLocalFileSystemPath(@NotNull VirtualFile file) {
    if (file.getFileType() == FileTypes.ARCHIVE) {
      final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jar != null) {
        return jar.getPath();
      }
    }
    return toCanonicalPath(file.getPath());
  }

  @Nullable
  public static ExternalSystemManager<?, ?, ?, ?, ?> getManager(@NotNull ProjectSystemId externalSystemId) {
    return MANAGERS.getValue().get(externalSystemId);
  }

  public static Collection<ExternalSystemManager<?, ?, ?, ?, ?>> getAllManagers() {
    return MANAGERS.getValue().values();
  }

  @NotNull
  public static Map<Key<?>, List<DataNode<?>>> group(@NotNull Collection<DataNode<?>> nodes) {
    return groupBy(nodes, GROUPER);
  }

  @NotNull
  public static <K, V> Map<DataNode<K>, List<DataNode<V>>> groupBy(@NotNull Collection<DataNode<V>> nodes, @NotNull final Key<K> key) {
    return groupBy(nodes, new NullableFunction<DataNode<V>, DataNode<K>>() {
      @Nullable
      @Override
      public DataNode<K> fun(DataNode<V> node) {
        return node.getDataNode(key);
      }
    });
  }

  @NotNull
  public static <K, V> Map<K, List<V>> groupBy(@NotNull Collection<V> nodes, @NotNull NullableFunction<V, K> grouper) {
    Map<K, List<V>> result = ContainerUtilRt.newHashMap();
    for (V data : nodes) {
      K key = grouper.fun(data);
      if (key == null) {
        LOG.warn(String.format(
          "Skipping entry '%s' during grouping. Reason: it's not possible to build a grouping key with grouping strategy '%s'. "
          + "Given entries: %s",
          data,
          grouper.getClass(),
          nodes));
        continue;
      }
      List<V> grouped = result.get(key);
      if (grouped == null) {
        result.put(key, grouped = ContainerUtilRt.newArrayList());
      }
      grouped.add(data);
    }

    if (!result.isEmpty() && result.keySet().iterator().next() instanceof Comparable) {
      List<K> ordered = ContainerUtilRt.newArrayList(result.keySet());
      Collections.sort(ordered, COMPARABLE_GLUE);
      Map<K, List<V>> orderedResult = ContainerUtilRt.newLinkedHashMap();
      for (K k : ordered) {
        orderedResult.put(k, result.get(k));
      }
      return orderedResult;
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

  public static void executeProjectChangeAction(@NotNull final Runnable task) {
    executeProjectChangeAction(false, task);
  }

  public static void executeProjectChangeAction(boolean synchronous, @NotNull final Runnable task) {
    executeOnEdt(synchronous, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            task.run();
          }
        });
      }
    });
  }
  
  public static void executeOnEdt(boolean synchronous, @NotNull Runnable task) {
    if (synchronous) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        task.run();
      }
      else {
        UIUtil.invokeAndWaitIfNeeded(task);
      }
    }
    else {
      UIUtil.invokeLaterIfNeeded(task);
    }
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
    String root = PathManager.getResourceRoot(contextClass, pathToUse);
    if (root != null) {
      classPath.add(root);
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  public static String normalizePath(@Nullable String s) {
    return StringUtil.isEmpty(s) ? null : s.replace('\\', ExternalSystemConstants.PATH_SEPARATOR);
  }

  /**
   * We can divide all 'import from external system' use-cases into at least as below:
   * <pre>
   * <ul>
   *   <li>this is a new project being created (import project from external model);</li>
   *   <li>a new module is being imported from an external project into an existing ide project;</li>
   * </ul>
   * </pre>
   * This method allows to differentiate between them (e.g. we don't want to change language level when new module is imported to
   * an existing project).
   * 
   * @return    <code>true</code> if new project is being imported; <code>false</code> if new module is being imported
   */
  public static boolean isNewProjectConstruction() {
    return ProjectManager.getInstance().getOpenProjects().length == 0;
  }

  @NotNull
  public static String getLastUsedExternalProjectPath(@NotNull ProjectSystemId externalSystemId) {
    return PropertiesComponent.getInstance().getValue(LAST_USED_PROJECT_PATH_PREFIX + externalSystemId.getReadableName(), "");
  }

  public static void storeLastUsedExternalProjectPath(@Nullable String path, @NotNull ProjectSystemId externalSystemId) {
    if (path != null) {
      PropertiesComponent.getInstance().setValue(LAST_USED_PROJECT_PATH_PREFIX + externalSystemId.getReadableName(), path);
    }
  }

  @NotNull
  public static String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
    if (rootProjectPath == null) {
      File rootProjectDir = new File(targetProjectPath);
      if (rootProjectDir.isFile()) {
        rootProjectDir = rootProjectDir.getParentFile();
      }
      return rootProjectDir.getName();
    }
    File rootProjectDir = new File(rootProjectPath);
    if (rootProjectDir.isFile()) {
      rootProjectDir = rootProjectDir.getParentFile();
    }
    File targetProjectDir = new File(targetProjectPath);
    if (targetProjectDir.isFile()) {
      targetProjectDir = targetProjectDir.getParentFile();
    }
    StringBuilder buffer = new StringBuilder();
    for (File f = targetProjectDir; f != null && !FileUtil.filesEqual(f, rootProjectDir); f = f.getParentFile()) {
      buffer.insert(0, f.getName()).insert(0, ":");
    }
    buffer.insert(0, rootProjectDir.getName());
    return buffer.toString();
  }

  /**
   * There is a possible case that external project linked to an ide project is a multi-project, i.e. contains more than one
   * module.
   * <p/>
   * This method tries to find root project's config path assuming that given path points to a sub-project's config path.
   * 
   * @param externalProjectPath  external sub-project's config path
   * @param externalSystemId     target external system
   * @param project              target ide project
   * @return                     root external project's path if given path is considered to point to a known sub-project's config;
   *                             <code>null</code> if it's not possible to find a root project's config path on the basis of the
   *                             given path
   */
  @Nullable
  public static String getRootProjectPath(@NotNull String externalProjectPath,
                                          @NotNull ProjectSystemId externalSystemId,
                                          @NotNull Project project)
  {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(externalSystemId);
    if (manager == null) {
      return null;
    }
    if (manager instanceof ExternalSystemAutoImportAware) {
      return ((ExternalSystemAutoImportAware)manager).getAffectedExternalProjectPath(externalProjectPath, project);
    }
    return null;
  }

  /**
   * {@link RemoteUtil#unwrap(Throwable) unwraps} given exception if possible and builds error message for it.
   *
   * @param e  exception to process
   * @return error message for the given exception
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
}
