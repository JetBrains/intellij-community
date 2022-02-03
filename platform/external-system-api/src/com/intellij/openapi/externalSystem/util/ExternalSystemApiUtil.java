// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.PathsList;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExternalSystemApiUtil {
  @NotNull public static final String PATH_SEPARATOR = "/";

  @NotNull public static final Comparator<Object> ORDER_AWARE_COMPARATOR = new Comparator<>() {

    @Override
    public int compare(@NotNull Object o1, @NotNull Object o2) {
      int order1 = getOrder(o1);
      int order2 = getOrder(o2);
      return Integer.compare(order1, order2);
    }

    private int getOrder(@NotNull Object o) {
      Queue<Class<?>> toCheck = new ArrayDeque<>();
      toCheck.add(o.getClass());
      while (!toCheck.isEmpty()) {
        Class<?> clazz = toCheck.poll();
        Order annotation = clazz.getAnnotation(Order.class);
        if (annotation != null) {
          return annotation.value();
        }
        Class<?> c = clazz.getSuperclass();
        if (c != null) {
          toCheck.add(c);
        }
        Class<?>[] interfaces = clazz.getInterfaces();
        Collections.addAll(toCheck, interfaces);
      }
      return ExternalSystemConstants.UNORDERED;
    }
  };

  @NotNull private static final NullableFunction<DataNode<?>, Key<?>> GROUPER = node -> node.getKey();

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

  public static boolean isRelated(@NotNull Library library, @NotNull LibraryData libraryData) {
    return getLibraryName(library).equals(libraryData.getInternalName());
  }

  public static boolean isExternalSystemLibrary(@NotNull Library library, @NotNull ProjectSystemId externalSystemId) {
    return library.getName() != null && StringUtil.startsWithIgnoreCase(library.getName(), externalSystemId.getId() + ": ");
  }

  public static void orderAwareSort(@NotNull List<?> data) {
    data.sort(ORDER_AWARE_COMPARATOR);
  }

  /**
   * @param path target path
   * @return path that points to the same location as the given one and that uses only slashes
   */
  @NotNull
  public static String toCanonicalPath(@NotNull String path) {
    String p = normalizePath(path);
    assert p != null;
    return FileUtil.toCanonicalPath(p);
  }

  @NotNull
  public static String getLocalFileSystemPath(@NotNull VirtualFile file) {
    if (FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE)) {
      final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jar != null) {
        return jar.getPath();
      }
    }
    return toCanonicalPath(file.getPath());
  }

  @Nullable
  public static ExternalSystemManager<?, ?, ?, ?, ?> getManager(@NotNull ProjectSystemId externalSystemId) {
    return ExternalSystemManager.EP_NAME.findFirstSafe(manager -> externalSystemId.equals(manager.getSystemId()));
  }

  @NotNull
  public static List<ExternalSystemManager<?, ?, ?, ?, ?>> getAllManagers() {
    return ExternalSystemManager.EP_NAME.getExtensionList();
  }

  public static MultiMap<Key<?>, DataNode<?>> recursiveGroup(@NotNull Collection<? extends DataNode<?>> nodes) {
    MultiMap<Key<?>, DataNode<?>> result = new ContainerUtil.KeyOrderedMultiMap<>();
    Queue<Collection<? extends DataNode<?>>> queue = new LinkedList<>();
    queue.add(nodes);
    while (!queue.isEmpty()) {
      Collection<? extends DataNode<?>> _nodes = queue.remove();
      result.putAllValues(group(_nodes));
      for (DataNode<?> _node : _nodes) {
        queue.add(_node.getChildren());
      }
    }
    return result;
  }

  @NotNull
  public static MultiMap<Key<?>, DataNode<?>> group(@NotNull Collection<? extends DataNode<?>> nodes) {
    return ContainerUtil.groupBy(nodes, GROUPER);
  }

  @NotNull
  public static <K, V> MultiMap<DataNode<K>, DataNode<V>> groupBy(@NotNull Collection<? extends DataNode<V>> nodes, final Class<K> moduleDataClass) {
    return ContainerUtil.groupBy(nodes, node -> node.getParent(moduleDataClass));
  }

  @NotNull
  public static <K, V> MultiMap<DataNode<K>, DataNode<V>> groupBy(@NotNull Collection<? extends DataNode<V>> nodes, @NotNull final Key<K> key) {
    return ContainerUtil.groupBy(nodes, node -> node.getDataNode(key));
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
        result = new ArrayList<>();
      }
      result.add((DataNode<T>)child);
    }
    return result == null ? Collections.emptyList() : result;
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
  public static <T> DataNode<T> find(@NotNull DataNode<?> node, @NotNull Key<T> key, BooleanFunction<? super DataNode<T>> predicate) {
    for (DataNode<?> child : node.getChildren()) {
      if (key.equals(child.getKey()) && predicate.fun((DataNode<T>)child)) {
        return (DataNode<T>)child;
      }
    }
    return null;
  }

  @Nullable
  public static <T> DataNode<T> findParent(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    return findParent(node, key, null);
  }


  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> DataNode<T> findParent(@NotNull DataNode<?> node,
                                           @NotNull Key<T> key,
                                           @Nullable BooleanFunction<? super DataNode<T>> predicate) {
    DataNode<?> parent = node.getParent();
    if (parent == null) return null;
    return key.equals(parent.getKey()) && (predicate == null || predicate.fun((DataNode<T>)parent))
           ? (DataNode<T>)parent : findParent(parent, key, predicate);
  }

  @NotNull
  public static <T> Collection<DataNode<T>> findAll(@NotNull DataNode<?> parent, @NotNull Key<T> key) {
    return getChildren(parent, key);
  }

  public static void visit(@Nullable DataNode<?> originalNode, @NotNull Consumer<? super DataNode<?>> consumer) {
    if (originalNode != null) {
      originalNode.visit(consumer);
    }
  }

  @NotNull
  public static <T> Collection<DataNode<T>> findAllRecursively(@Nullable DataNode<?> node, @NotNull Key<T> key) {
    if (node == null) {
      return Collections.emptyList();
    }

    //noinspection unchecked
    return (Collection)findAllRecursively(node.getChildren(), it -> it.getKey().equals(key));
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@NotNull Collection<? extends DataNode<?>> nodes) {
    return findAllRecursively(nodes, null);
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@Nullable DataNode<?> node,
                                                           @Nullable Predicate<? super DataNode<?>> predicate) {
    if (node == null) return Collections.emptyList();
    return findAllRecursively(node.getChildren(), predicate);
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@NotNull Collection<? extends DataNode<?>> nodes,
                                                           @Nullable Predicate<? super DataNode<?>> predicate) {
    List<DataNode<?>> result = new ArrayList<>();
    for (DataNode<?> node : nodes) {
      if (predicate == null || predicate.test(node)) {
        result.add(node);
      }
    }
    for (DataNode<?> node : nodes) {
      result.addAll(findAllRecursively(node.getChildren(), predicate));
    }
    return result;
  }

  @Nullable
  public static DataNode<?> findFirstRecursively(@NotNull DataNode<?> parentNode,
                                                 @NotNull Predicate<? super DataNode<?>> predicate) {
    Queue<DataNode<?>> queue = new LinkedList<>();
    queue.add(parentNode);
    return findInQueue(queue, predicate);
  }

  @Nullable
  public static DataNode<?> findFirstRecursively(@NotNull Collection<? extends DataNode<?>> nodes,
                                                 @NotNull Predicate<? super DataNode<?>> predicate) {
    return findInQueue(new LinkedList<>(nodes), predicate);
  }

  @Nullable
  private static DataNode<?> findInQueue(@NotNull Queue<DataNode<?>> queue,
                                         @NotNull Predicate<? super DataNode<?>> predicate) {
    while (!queue.isEmpty()) {
      DataNode<?> node = queue.remove();
      if (predicate.test(node)) {
        return node;
      }
      queue.addAll(node.getChildren());
    }
    return null;
  }

  public static void executeProjectChangeAction(@NotNull final DisposeAwareProjectChange task) {
    executeProjectChangeAction(true, task);
  }

  public static void executeProjectChangeAction(boolean synchronous, @NotNull final DisposeAwareProjectChange task) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    }
    executeOnEdt(synchronous, () -> ApplicationManager.getApplication().runWriteAction(task));
  }

  public static void executeOnEdt(boolean synchronous, @NotNull Runnable task) {
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      task.run();
      return;
    }

    if (synchronous) {
      app.invokeAndWait(task);
    }
    else {
      app.invokeLater(task);
    }
  }

  public static <T> T executeOnEdt(@NotNull final Computable<T> task) {
    final Application app = ApplicationManager.getApplication();
    final Ref<T> result = Ref.create();
    app.invokeAndWait(() -> result.set(task.compute()));
    return result.get();
  }

  public static <T> T doWriteAction(@NotNull final Computable<T> task) {
    return executeOnEdt(() -> ApplicationManager.getApplication().runWriteAction(task));
  }

  public static void doWriteAction(@NotNull final Runnable task) {
    executeOnEdt(true, () -> ApplicationManager.getApplication().runWriteAction(task));
  }

  /**
   * Adds runnable to Event Dispatch Queue
   * if we aren't in UnitTest of Headless environment mode
   *
   * @param runnable Runnable
   */
  public static void addToInvokeLater(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    final boolean unitTestMode = application.isUnitTestMode();
    if (unitTestMode) {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
    else if (application.isHeadlessEnvironment() || application.isDispatchThread()) {
      runnable.run();
    }
    else {
      EdtExecutorService.getInstance().execute(runnable);
    }
  }

  /**
   * @deprecated there is no need to call this method since we don't put message bundles to separate resources_en.jar files (IDEA-255246)
   */
  @Deprecated
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

  @Nullable
  public static String normalizePath(@Nullable String s) {
    return s == null ? null : s.replace('\\', ExternalSystemConstants.PATH_SEPARATOR);
  }

  /**
   * Allows to answer if given ide project has 1-1 mapping with the given external project, i.e. the ide project has been
   * imported from external system and no other external projects have been added.
   * <p/>
   * This might be necessary in a situation when project-level setting is changed (e.g. project name). We don't want to rename
   * ide project if it doesn't completely corresponds to the given ide project then.
   *
   * @param ideProject  target ide project
   * @param projectData target external project
   * @param modules     the list of modules to check (during import this contains uncommitted modules from the modifiable model)
   * @return {@code true} if given ide project has 1-1 mapping to the given external project;
   * {@code false} otherwise
   */
  public static boolean isOneToOneMapping(@NotNull Project ideProject, @NotNull ProjectData projectData, Module[] modules) {
    String linkedExternalProjectPath = null;
    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemManager.EP_NAME.getIterable()) {
      ProjectSystemId externalSystemId = manager.getSystemId();
      AbstractExternalSystemSettings systemSettings = getSettings(ideProject, externalSystemId);
      Collection projectsSettings = systemSettings.getLinkedProjectsSettings();
      int linkedProjectsNumber = projectsSettings.size();
      if (linkedProjectsNumber > 1) {
        // More than one external project of the same external system type is linked to the given ide project.
        return false;
      }
      else if (linkedProjectsNumber == 1) {
        if (linkedExternalProjectPath == null) {
          // More than one external project of different external system types is linked to the current ide project.
          linkedExternalProjectPath = ((ExternalProjectSettings)projectsSettings.iterator().next()).getExternalProjectPath();
        }
        else {
          return false;
        }
      }
    }

    if (linkedExternalProjectPath != null && !linkedExternalProjectPath.equals(projectData.getLinkedExternalProjectPath())) {
      // New external project is being linked.
      return false;
    }

    for (Module module : modules) {
      if (!isExternalSystemAwareModule(projectData.getOwner(), module)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public static @NlsSafe String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
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
   * @param externalProjectPath external sub-project's config path
   * @param externalSystemId    target external system
   * @param project             target ide project
   * @return root external project's path if given path is considered to point to a known sub-project's config;
   * {@code null} if it's not possible to find a root project's config path on the basis of the
   * given path
   */
  @Nullable
  public static String getRootProjectPath(@NotNull String externalProjectPath,
                                          @NotNull ProjectSystemId externalSystemId,
                                          @NotNull Project project) {
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
   * @param e exception to process
   * @return error message for the given exception
   */
  @NotNull
  public static @Nls String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == ExternalSystemException.class) {
      String originalReason = ((ExternalSystemException)unwrapped).getOriginalReason();
      return ExternalSystemBundle.message("external.system.api.error.message.prefix", originalReason);
    }
    else {
      return stacktraceAsString(unwrapped);
    }
  }

  @NotNull
  public static @NlsSafe String stacktraceAsString(@NotNull Throwable throwable) {
    Throwable unwrapped = RemoteUtil.unwrap(throwable);
    StringWriter writer = new StringWriter();
    unwrapped.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  @NotNull
  public static AbstractExternalSystemSettings getSettings(@NotNull Project project, @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(externalSystemId);
    if (manager == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve external system settings for id '%s'. Reason: no such external system is registered",
        externalSystemId.getReadableName()
      ));
    }
    return manager.getSettingsProvider().fun(project);
  }

  @SuppressWarnings("unchecked")
  public static <S extends AbstractExternalSystemLocalSettings> S getLocalSettings(@NotNull Project project,
                                                                                   @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(externalSystemId);
    if (manager == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve local external system settings for id '%s'. Reason: no such external system is registered",
        externalSystemId.getReadableName()
      ));
    }
    return (S)manager.getLocalSettingsProvider().fun(project);
  }

  @SuppressWarnings("unchecked")
  public static <S extends ExternalSystemExecutionSettings> S getExecutionSettings(@NotNull Project project,
                                                                                   @NotNull String linkedProjectPath,
                                                                                   @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(externalSystemId);
    if (manager == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve external system execution settings for id '%s'. Reason: no such external system is registered",
        externalSystemId.getReadableName()
      ));
    }
    return (S)manager.getExecutionSettingsProvider().fun(Pair.create(project, linkedProjectPath));
  }

  /**
   * Historically we prefer to work with third-party api not from ide process but from dedicated slave process (there is a risk
   * that third-party api has bugs which might make the whole ide process corrupted, e.g. a memory leak at the api might crash
   * the whole ide process).
   * <p/>
   * However, we do allow to explicitly configure the ide to work with third-party external system api from the ide process.
   * <p/>
   * This method allows to check whether the ide is configured to use 'out of process' or 'in process' mode for the system.
   *
   * @param externalSystemId target external system
   * @return {@code true} if the ide is configured to work with external system api from the ide process;
   * {@code false} otherwise
   */
  public static boolean isInProcessMode(ProjectSystemId externalSystemId) {
    return Registry.is(externalSystemId.getId() + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX, false);
  }

  public static ProjectModelExternalSource toExternalSource(@NotNull ProjectSystemId systemId) {
    return ExternalProjectSystemRegistry.getInstance().getSourceById(systemId.getId());
  }

  @Contract(value = "_, null -> false", pure = true)
  public static boolean isExternalSystemAwareModule(@NotNull ProjectSystemId systemId, @Nullable Module module) {
    return module != null &&
           !module.isDisposed() &&
           systemId.getId().equals(ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId());
  }

  @Contract(value = "_, null -> false", pure = true)
  public static boolean isExternalSystemAwareModule(@NotNull String systemId, @Nullable Module module) {
    return module != null &&
           !module.isDisposed() &&
           systemId.equals(ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId());
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectPath() : null;
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalRootProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath() : null;
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalProjectId(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectId() : null;
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalProjectGroup(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleGroup() : null;
  }

  private static final ExtensionPointName<ExternalSystemContentRootContributor> ExternalSystemContentRootContributorEP =
    ExtensionPointName.create("com.intellij.externalSystemContentRootContributor");

  @Contract(pure = true)
  public static @Nullable Collection<ExternalSystemContentRootContributor.@NotNull ExternalContentRoot> getExternalProjectContentRoots(
    @NotNull Module module,
    @NotNull Collection<@NotNull ExternalSystemSourceType> sourceTypes
  ) {
    if (module.isDisposed()) return null;
    String systemId = ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId();
    if (systemId == null) return null;
    ExternalSystemContentRootContributor contributor =
      ExternalSystemContentRootContributorEP.findFirstSafe((c) -> c.isApplicable(systemId));

    if (contributor == null) return null;
    return contributor.findContentRoots(module, sourceTypes);
  }

  @Contract(pure = true)
  public static @Nullable Collection<ExternalSystemContentRootContributor.@NotNull ExternalContentRoot> getExternalProjectContentRoots(
    @NotNull Module module,
    @NotNull ExternalSystemSourceType sourceType
  ) {
    return getExternalProjectContentRoots(module, List.of(sourceType));
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalProjectVersion(@Nullable Module module) {
    return module != null && !module.isDisposed()
           ? ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleVersion()
           : null;
  }

  @Nullable
  @Contract(pure = true)
  public static String getExternalModuleType(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleType() : null;
  }

  public static void subscribe(@NotNull Project project,
                               @NotNull ProjectSystemId systemId,
                               @NotNull ExternalSystemSettingsListener listener) {
    //noinspection unchecked
    getSettings(project, systemId).subscribe(listener, project);
  }

  public static void subscribe(@NotNull Project project,
                               @NotNull ProjectSystemId systemId,
                               @NotNull ExternalSystemSettingsListener listener,
                               @NotNull Disposable parentDisposable) {
    //noinspection unchecked
    getSettings(project, systemId).subscribe(listener, parentDisposable);
  }

  @NotNull
  public static Collection<TaskData> findProjectTasks(@NotNull Project project,
                                                      @NotNull ProjectSystemId systemId,
                                                      @NotNull String projectPath) {
    AbstractExternalSystemSettings settings = getSettings(project, systemId);
    ExternalProjectSettings linkedProjectSettings = settings.getLinkedProjectSettings(projectPath);
    if (linkedProjectSettings == null) return Collections.emptyList();

    ExternalProjectInfo projectInfo = ContainerUtil.find(
      ProjectDataManager.getInstance().getExternalProjectsData(project, systemId),
      info -> FileUtil.pathsEqual(linkedProjectSettings.getExternalProjectPath(), info.getExternalProjectPath())
    );

    if (projectInfo == null) return Collections.emptyList();
    DataNode<ProjectData> projectStructure = projectInfo.getExternalProjectStructure();
    if (projectStructure == null) return Collections.emptyList();

    List<TaskData> tasks = new SmartList<>();

    DataNode<ModuleData> moduleDataNode = ContainerUtil.find(
      findAll(projectStructure, ProjectKeys.MODULE),
      moduleNode -> FileUtil.pathsEqual(projectPath, moduleNode.getData().getLinkedExternalProjectPath())
    );
    if (moduleDataNode == null) return Collections.emptyList();

    findAll(moduleDataNode, ProjectKeys.TASK).stream().map(DataNode::getData).forEach(tasks::add);
    return tasks;
  }

  @ApiStatus.Experimental
  @Nullable
  public static DataNode<ProjectData> findProjectData(@NotNull Project project,
                                                      @NotNull ProjectSystemId systemId,
                                                      @NotNull String projectPath) {
    ExternalProjectInfo projectInfo = findProjectInfo(project, systemId, projectPath);
    if (projectInfo == null) return null;
    return projectInfo.getExternalProjectStructure();
  }

  @ApiStatus.Experimental
  @Nullable
  public static ExternalProjectInfo findProjectInfo(@NotNull Project project,
                                                    @NotNull ProjectSystemId systemId,
                                                    @NotNull String projectPath) {
    AbstractExternalSystemSettings settings = getSettings(project, systemId);
    ExternalProjectSettings linkedProjectSettings = settings.getLinkedProjectSettings(projectPath);
    if (linkedProjectSettings == null) return null;
    String rootProjectPath = linkedProjectSettings.getExternalProjectPath();
    return ProjectDataManager.getInstance().getExternalProjectData(project, systemId, rootProjectPath);
  }

  public static @NotNull FileChooserDescriptor getExternalProjectConfigDescriptor(@NotNull ProjectSystemId systemId) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(systemId);
    if (manager instanceof ExternalSystemUiAware) {
      ExternalSystemUiAware uiAware = ((ExternalSystemUiAware)manager);
      FileChooserDescriptor descriptor = uiAware.getExternalProjectConfigDescriptor();
      if (descriptor != null) {
        return descriptor;
      }
    }
    return FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
  }
}
