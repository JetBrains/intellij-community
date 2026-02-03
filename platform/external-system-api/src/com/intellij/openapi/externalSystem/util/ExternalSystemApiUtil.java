// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ComponentManager;
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
  public static final @NotNull String PATH_SEPARATOR = "/";

  public static final @NotNull Comparator<Object> ORDER_AWARE_COMPARATOR = new Comparator<>() {

    @Override
    public int compare(@NotNull Object o1, @NotNull Object o2) {
      int order1 = getOrder(o1);
      int order2 = getOrder(o2);
      return Integer.compare(order1, order2);
    }

    private static int getOrder(@NotNull Object o) {
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

  private static final @NotNull NullableFunction<DataNode<?>, Key<?>> GROUPER = node -> node.getKey();

  private ExternalSystemApiUtil() {
  }

  public static @NotNull String extractNameFromPath(@NotNull String path) {
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

  private static @NotNull String stripPath(@NotNull String path) {
    String[] endingsToStrip = {"/", "!", ".jar"};
    StringBuilder buffer = new StringBuilder(path);
    for (String ending : endingsToStrip) {
      if (buffer.lastIndexOf(ending) == buffer.length() - ending.length()) {
        buffer.setLength(buffer.length() - ending.length());
      }
    }
    return buffer.toString();
  }

  public static @NotNull String getLibraryName(@NotNull Library library) {
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

  @Contract(mutates = "param1")
  public static void orderAwareSort(@NotNull List<?> data) {
    data.sort(ORDER_AWARE_COMPARATOR);
  }

  /**
   * @param path target path
   * @return path that points to the same location as the given one and that uses only slashes
   */
  public static @NotNull String toCanonicalPath(@NotNull String path) {
    String p = normalizePath(path);
    assert p != null;
    return FileUtil.toCanonicalPath(p);
  }

  public static @NotNull String getLocalFileSystemPath(@NotNull VirtualFile file) {
    if (FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE)) {
      final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jar != null) {
        return jar.getPath();
      }
    }
    return toCanonicalPath(file.getPath());
  }

  public static @Nullable ExternalSystemManager<?, ?, ?, ?, ?> getManager(@NotNull ProjectSystemId externalSystemId) {
    return ExternalSystemManager.EP_NAME.findFirstSafe(manager -> externalSystemId.equals(manager.getSystemId()));
  }

  public static @NotNull List<ExternalSystemManager<?, ?, ?, ?, ?>> getAllManagers() {
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

  public static @NotNull MultiMap<Key<?>, DataNode<?>> group(@NotNull Collection<? extends DataNode<?>> nodes) {
    return ContainerUtil.groupBy(nodes, GROUPER);
  }

  public static @NotNull <K, V> MultiMap<DataNode<K>, DataNode<V>> groupBy(@NotNull Collection<? extends DataNode<V>> nodes, final Class<K> moduleDataClass) {
    return ContainerUtil.groupBy(nodes, node -> node.getParent(moduleDataClass));
  }

  public static @NotNull <K, V> MultiMap<DataNode<K>, DataNode<V>> groupBy(@NotNull Collection<? extends DataNode<V>> nodes, final @NotNull Key<K> key) {
    return ContainerUtil.groupBy(nodes, node -> node.getDataNode(key));
  }

  public static @NotNull @Unmodifiable <T> Collection<DataNode<T>> getChildren(@NotNull DataNode<?> node, @NotNull Key<T> key) {
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

  public static @Nullable <T> DataNode<T> find(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    return findChild(node, key, null);
  }

  public static @Nullable <T> DataNode<T> findChild(
    @NotNull DataNode<?> node,
    @NotNull Key<T> key,
    @Nullable Predicate<? super DataNode<T>> predicate
  ) {
    for (DataNode<?> child : node.getChildren()) {
      if (key.equals(child.getKey())) {
        //noinspection unchecked
        var childNode = (DataNode<T>)child;
        if (predicate == null || predicate.test(childNode)) {
          return childNode;
        }
      }
    }
    return null;
  }

  public static @Nullable <T> DataNode<T> findParent(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    return findParentRecursively(node, key, null);
  }

  public static @Nullable <T> DataNode<T> findParentRecursively(
    @NotNull DataNode<?> node,
    @NotNull Key<T> key,
    @Nullable Predicate<? super DataNode<T>> predicate
  ) {
    var parent = node.getParent();
    if (parent == null) {
      return null;
    }
    if (key.equals(parent.getKey())) {
      //noinspection unchecked
      var parentNode = (DataNode<T>)parent;
      if (predicate == null || predicate.test(parentNode)) {
        return parentNode;
      }
    }
    return findParentRecursively(parent, key, predicate);
  }

  /**
   * @deprecated Use findChild instead
   */
  @Deprecated(forRemoval = true)
  public static @Nullable <T> DataNode<T> find(
    @NotNull DataNode<?> node,
    @NotNull Key<T> key,
    @Nullable BooleanFunction<? super DataNode<T>> predicate
  ) {
    if (predicate == null) {
      return findChild(node, key, null);
    }
    else {
      return findChild(node, key, it -> predicate.fun(it));
    }
  }

  public static @NotNull @Unmodifiable <T> Collection<DataNode<T>> findAll(@NotNull DataNode<?> parent, @NotNull Key<T> key) {
    return getChildren(parent, key);
  }

  public static void visit(@Nullable DataNode<?> originalNode, @NotNull Consumer<? super DataNode<?>> consumer) {
    if (originalNode != null) {
      originalNode.visit(consumer);
    }
  }

  public static @NotNull <T> Collection<DataNode<T>> findAllRecursively(@Nullable DataNode<?> node, @NotNull Key<T> key) {
    if (node == null) {
      return Collections.emptyList();
    }

    //noinspection unchecked
    return (Collection)findAllRecursively(node.getChildren(), it -> it.getKey().equals(key));
  }

  public static @NotNull Collection<DataNode<?>> findAllRecursively(@NotNull Collection<? extends DataNode<?>> nodes) {
    return findAllRecursively(nodes, null);
  }

  public static @NotNull Collection<DataNode<?>> findAllRecursively(@Nullable DataNode<?> node,
                                                                    @Nullable Predicate<? super DataNode<?>> predicate) {
    if (node == null) return Collections.emptyList();
    return findAllRecursively(node.getChildren(), predicate);
  }

  public static @NotNull Collection<DataNode<?>> findAllRecursively(@NotNull Collection<? extends DataNode<?>> nodes,
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

  public static @Nullable DataNode<?> findFirstRecursively(@NotNull DataNode<?> parentNode,
                                                           @NotNull Predicate<? super DataNode<?>> predicate) {
    Queue<DataNode<?>> queue = new LinkedList<>();
    queue.add(parentNode);
    return findInQueue(queue, predicate);
  }

  public static @Nullable DataNode<?> findFirstRecursively(@NotNull Collection<? extends DataNode<?>> nodes,
                                                           @NotNull Predicate<? super DataNode<?>> predicate) {
    return findInQueue(new LinkedList<>(nodes), predicate);
  }

  private static @Nullable DataNode<?> findInQueue(@NotNull Queue<DataNode<?>> queue,
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

  public static void executeProjectChangeAction(@NotNull ComponentManager componentManager, @NotNull Runnable task) {
    executeProjectChangeAction(true, componentManager, task);
  }

  /**
   * @deprecated Use executeProjectChangeAction(ComponentManager, Runnable) instead.
   */
  @Deprecated(forRemoval = true)
  public static void executeProjectChangeAction(final @NotNull DisposeAwareProjectChange task) {
    executeProjectChangeAction(true, task);
  }

  public static void executeProjectChangeAction(boolean synchronous, @NotNull ComponentManager componentManager, @NotNull Runnable task) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    }
    executeOnEdt(synchronous, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!componentManager.isDisposed()) {
        task.run();
      }
    }));
  }

  /**
   * @deprecated Use executeProjectChangeAction(boolean, ComponentManager, Runnable) instead.
   */
  @Deprecated(forRemoval = true)
  public static void executeProjectChangeAction(boolean synchronous, final @NotNull DisposeAwareProjectChange task) {
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

  public static <T> T executeOnEdt(final @NotNull Computable<T> task) {
    final Application app = ApplicationManager.getApplication();
    final Ref<T> result = Ref.create();
    app.invokeAndWait(() -> result.set(task.compute()));
    return result.get();
  }

  public static <T> T doWriteAction(final @NotNull Computable<T> task) {
    return executeOnEdt(() -> ApplicationManager.getApplication().runWriteAction(task));
  }

  public static void doWriteAction(final @NotNull Runnable task) {
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

  public static @Nullable String normalizePath(@Nullable String s) {
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

  public static @NotNull @NlsSafe String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
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
  public static @Nullable String getRootProjectPath(@NotNull String externalProjectPath,
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
  public static @NotNull @Nls String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == ExternalSystemException.class) {
      String originalReason = ((ExternalSystemException)unwrapped).getOriginalReason();
      if (originalReason.isBlank()) {
        return stacktraceAsString(unwrapped);
      }
      return ExternalSystemBundle.message("external.system.api.error.message.prefix", originalReason);
    }
    else {
      return stacktraceAsString(unwrapped);
    }
  }

  public static @NotNull @NlsSafe String stacktraceAsString(@NotNull Throwable throwable) {
    Throwable unwrapped = RemoteUtil.unwrap(throwable);
    StringWriter writer = new StringWriter();
    unwrapped.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  public static @NotNull AbstractExternalSystemSettings getSettings(@NotNull Project project, @NotNull ProjectSystemId externalSystemId)
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

  @Contract(pure = true)
  public static @Nullable String getExternalProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectPath() : null;
  }

  @Contract(pure = true)
  public static @Nullable String getExternalRootProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath() : null;
  }

  @Contract(pure = true)
  public static @Nullable String getExternalProjectId(@Nullable Module module) {
    return module != null && !module.isDisposed() ? ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectId() : null;
  }

  @Contract(pure = true)
  public static @Nullable String getExternalProjectGroup(@Nullable Module module) {
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

  @Contract(pure = true)
  public static @Nullable String getExternalProjectVersion(@Nullable Module module) {
    return module != null && !module.isDisposed()
           ? ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleVersion()
           : null;
  }

  @Contract(pure = true)
  public static @Nullable String getExternalModuleType(@Nullable Module module) {
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

  public static @Unmodifiable @NotNull Collection<TaskData> findProjectTasks(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId,
    @NotNull String projectPath
  ) {
    var moduleDataNode = findModuleNode(project, systemId, projectPath);
    if (moduleDataNode == null) return Collections.emptyList();
    var taskNodes = findAll(moduleDataNode, ProjectKeys.TASK);
    return ContainerUtil.map(taskNodes, it -> it.getData());
  }

  public static @Nullable DataNode<ProjectData> findProjectNode(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId,
    @NotNull String projectPath
  ) {
    ExternalProjectInfo projectInfo = findProjectInfo(project, systemId, projectPath);
    if (projectInfo == null) return null;
    return projectInfo.getExternalProjectStructure();
  }

  public static @Nullable ExternalProjectInfo findProjectInfo(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId,
    @NotNull String projectPath
  ) {
    var settings = getSettings(project, systemId);
    var linkedProjectSettings = settings.getLinkedProjectSettings(projectPath);
    if (linkedProjectSettings == null) return null;
    var rootProjectPath = linkedProjectSettings.getExternalProjectPath();
    return ProjectDataManager.getInstance().getExternalProjectData(project, systemId, rootProjectPath);
  }

  public static @Nullable DataNode<ModuleData> findModuleNode(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId,
    @NotNull String projectPath
  ) {
    var projectNode = findProjectNode(project, systemId, projectPath);
    if (projectNode == null) return null;
    var moduleNodes = findAll(projectNode, ProjectKeys.MODULE);
    return ContainerUtil.find(moduleNodes, it -> FileUtil.pathsEqual(projectPath, it.getData().getLinkedExternalProjectPath()));
  }

  public static @NotNull FileChooserDescriptor getExternalProjectConfigDescriptor(@NotNull ProjectSystemId systemId) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(systemId);
    if (manager instanceof ExternalSystemUiAware uiAware) {
      FileChooserDescriptor descriptor = uiAware.getExternalProjectConfigDescriptor();
      if (descriptor != null) {
        return descriptor;
      }
    }
    return FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
  }
}
