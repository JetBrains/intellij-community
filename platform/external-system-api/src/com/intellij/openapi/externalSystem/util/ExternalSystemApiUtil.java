/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.ParametersEnhancer;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.containers.Stack;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.Queue;
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

  @NotNull public static final Comparator<Object> ORDER_AWARE_COMPARATOR = new Comparator<Object>() {

    @Override
    public int compare(@NotNull Object o1, @NotNull Object o2) {
      int order1 = getOrder(o1);
      int order2 = getOrder(o2);
      return (order1 < order2) ? -1 : ((order1 == order2) ? 0 : 1);
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

  @NotNull private static final TransferToEDTQueue<Runnable> TRANSFER_TO_EDT_QUEUE =
    new TransferToEDTQueue<Runnable>("External System queue", new Processor<Runnable>() {
      @Override
      public boolean process(Runnable runnable) {
        runnable.run();
        return true;
      }
    }, Conditions.alwaysFalse(), 300);

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
    return library.getName() != null && StringUtil.startsWith(library.getName(), externalSystemId.getReadableName() + ": ");
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
    for (ExternalSystemManager manager : ExternalSystemManager.EP_NAME.getExtensions()) {
      if (externalSystemId.equals(manager.getSystemId())) {
        return manager;
      }
    }
    return null;
  }

  @SuppressWarnings("ManualArrayToCollectionCopy")
  @NotNull
  public static Collection<ExternalSystemManager<?, ?, ?, ?, ?>> getAllManagers() {
    List<ExternalSystemManager<?, ?, ?, ?, ?>> result = ContainerUtilRt.newArrayList();
    for (ExternalSystemManager manager : ExternalSystemManager.EP_NAME.getExtensions()) {
      result.add(manager);
    }
    return result;
  }

  public static MultiMap<Key<?>, DataNode<?>> recursiveGroup(@NotNull Collection<DataNode<?>> nodes) {
    MultiMap<Key<?>, DataNode<?>> result = MultiMap.createLinked();
    Queue<Collection<DataNode<?>>> queue = ContainerUtil.newLinkedList();
    queue.add(nodes);
    while (!queue.isEmpty()) {
      Collection<DataNode<?>> _nodes = queue.remove();
      result.putAllValues(group(_nodes));
      for (DataNode<?> _node : _nodes) {
        queue.add(_node.getChildren());
      }
    }
    return result;
  }

  @NotNull
  public static MultiMap<Key<?>, DataNode<?>> group(@NotNull Collection<DataNode<?>> nodes) {
    return groupBy(nodes, GROUPER);
  }

  @NotNull
  public static <K, V> MultiMap<DataNode<K>, DataNode<V>> groupBy(@NotNull Collection<DataNode<V>> nodes, @NotNull final Key<K> key) {
    return groupBy(nodes, new NullableFunction<DataNode<V>, DataNode<K>>() {
      @Nullable
      @Override
      public DataNode<K> fun(DataNode<V> node) {
        return node.getDataNode(key);
      }
    });
  }

  @NotNull
  public static <K, V> MultiMap<K, V> groupBy(@NotNull Collection<V> nodes, @NotNull NullableFunction<V, K> grouper) {
    MultiMap<K, V> result = MultiMap.createLinked();
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
      result.putValue(key, data);
    }

    if (!result.isEmpty() && result.keySet().iterator().next() instanceof Comparable) {
      List<K> ordered = ContainerUtilRt.newArrayList(result.keySet());
      Collections.sort(ordered, COMPARABLE_GLUE);
      MultiMap<K, V> orderedResult = MultiMap.createLinked();
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
  @Nullable
  public static <T> DataNode<T> findParent(@NotNull DataNode<?> node, @NotNull Key<T> key) {
    return findParent(node, key, null);
  }


  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> DataNode<T> findParent(@NotNull DataNode<?> node,
                                           @NotNull Key<T> key,
                                           @Nullable BooleanFunction<DataNode<T>> predicate) {
    DataNode<?> parent = node.getParent();
    if (parent == null) return null;
    return key.equals(parent.getKey()) && (predicate == null || predicate.fun((DataNode<T>)parent))
           ? (DataNode<T>)parent : findParent(parent, key, predicate);
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

  public static void visit(@Nullable DataNode node, @NotNull Consumer<DataNode<?>> consumer) {
    if(node == null) return;

    Stack<DataNode> toProcess = ContainerUtil.newStack(node);
    while (!toProcess.isEmpty()) {
      DataNode<?> node0 = toProcess.pop();
      consumer.consume(node0);
      for (DataNode<?> child : node0.getChildren()) {
        toProcess.push(child);
      }
    }
  }

  @NotNull
  public static <T> Collection<DataNode<T>> findAllRecursively(@Nullable final DataNode<?> node,
                                                               @NotNull final Key<T> key) {
    if (node == null) return Collections.emptyList();

    final Collection<DataNode<?>> nodes = findAllRecursively(node.getChildren(), new BooleanFunction<DataNode<?>>() {
      @Override
      public boolean fun(DataNode<?> node) {
        return node.getKey().equals(key);
      }
    });
    //noinspection unchecked
    return new SmartList(nodes);
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@NotNull Collection<DataNode<?>> nodes) {
    return findAllRecursively(nodes, null);
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@Nullable DataNode<?> node,
                                                           @Nullable BooleanFunction<DataNode<?>> predicate) {
    if (node == null) return Collections.emptyList();
    return findAllRecursively(node.getChildren(), predicate);
  }

  @NotNull
  public static Collection<DataNode<?>> findAllRecursively(@NotNull Collection<DataNode<?>> nodes,
                                                           @Nullable BooleanFunction<DataNode<?>> predicate) {
    SmartList<DataNode<?>> result = new SmartList<DataNode<?>>();
    for (DataNode<?> node : nodes) {
      if (predicate == null || predicate.fun(node)) {
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
                                                 @NotNull BooleanFunction<DataNode<?>> predicate) {
    Queue<DataNode<?>> queue = new LinkedList<DataNode<?>>();
    queue.add(parentNode);
    return findInQueue(queue, predicate);
  }

  @Nullable
  public static DataNode<?> findFirstRecursively(@NotNull Collection<DataNode<?>> nodes,
                                                 @NotNull BooleanFunction<DataNode<?>> predicate) {
    return findInQueue(new LinkedList<DataNode<?>>(nodes), predicate);
  }

  @Nullable
  private static DataNode<?> findInQueue(@NotNull Queue<DataNode<?>> queue,
                                         @NotNull BooleanFunction<DataNode<?>> predicate) {
    while (!queue.isEmpty()) {
      DataNode node = (DataNode)queue.remove();
      if (predicate.fun(node)) {
        return node;
      }
      //noinspection unchecked
      queue.addAll(node.getChildren());
    }
    return null;
  }

  public static void executeProjectChangeAction(@NotNull final DisposeAwareProjectChange task) {
    executeProjectChangeAction(false, task);
  }

  public static void executeProjectChangeAction(boolean synchronous, @NotNull final DisposeAwareProjectChange task) {
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

  public static <T> T executeOnEdt(@NotNull Computable<T> task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return task.compute();
    }
    else {
      return UIUtil.invokeAndWaitIfNeeded(task);
    }
  }

  public static <T> T executeOnEdtUnderWriteAction(@NotNull final Computable<T> task) {
    return executeOnEdt(new Computable<T>() {
      public T compute() {
        return ApplicationManager.getApplication().runWriteAction(task);
      }
    });
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
      TRANSFER_TO_EDT_QUEUE.offer(runnable);
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

//  @NotNull
//  public static String getLastUsedExternalProjectPath(@NotNull ProjectSystemId externalSystemId) {
//    return PropertiesComponent.getInstance().getValue(LAST_USED_PROJECT_PATH_PREFIX + externalSystemId.getReadableName(), "");
//  }

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

  @SuppressWarnings("unchecked")
  @NotNull
  public static AbstractExternalSystemSettings getSettings(@NotNull Project project, @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException
  {
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
    throws IllegalArgumentException
  {
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
    throws IllegalArgumentException
  {
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
   * @param externalSystemId     target external system
   *
   * @return   <code>true</code> if the ide is configured to work with external system api from the ide process;
   *           <code>false</code> otherwise
   */
  public static boolean isInProcessMode(ProjectSystemId externalSystemId) {
    return Registry.is(externalSystemId.getId() + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX, false);
  }

  /**
   * There is a possible case that methods of particular object should be executed with classpath different from the one implied
   * by the current class' class loader. External system offers {@link ParametersEnhancer#enhanceLocalProcessing(List)} method
   * for defining that custom classpath.
   * <p/>
   * It's also possible that particular implementation of {@link ParametersEnhancer} is compiled using dependency to classes
   * which are provided by the {@link ParametersEnhancer#enhanceLocalProcessing(List) expanded classpath}. E.g. a class
   * <code>'A'</code> might use method of class <code>'B'</code> and 'A' is located at the current (system/plugin) classpath but
   * <code>'B'</code> is not. We need to reload <code>'A'</code> using its expanded classpath then, i.e. create new class loaded
   * with that expanded classpath and load <code>'A'</code> by it.
   * <p/>
   * This method allows to do that.
   *
   * @param clazz  custom classpath-aware class which instance should be created (is assumed to have a no-args constructor)
   * @param <T>    target type
   * @return       newly created instance of the given class loaded by custom classpath-aware loader
   * @throws IllegalAccessException     as defined by reflection processing
   * @throws InstantiationException     as defined by reflection processing
   * @throws NoSuchMethodException      as defined by reflection processing
   * @throws InvocationTargetException  as defined by reflection processing
   * @throws ClassNotFoundException     as defined by reflection processing
   */
  @NotNull
  public static <T extends ParametersEnhancer> T reloadIfNecessary(@NotNull final Class<T> clazz)
    throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException
  {
    T instance = clazz.newInstance();
    List<URL> urls = ContainerUtilRt.newArrayList();
    instance.enhanceLocalProcessing(urls);
    if (urls.isEmpty()) {
      return instance;
    }

    final ClassLoader baseLoader = clazz.getClassLoader();
    Method method = baseLoader.getClass().getMethod("getUrls");
    if (method != null) {
      //noinspection unchecked
      urls.addAll((Collection<? extends URL>)method.invoke(baseLoader));
    }
    UrlClassLoader loader = new UrlClassLoader(UrlClassLoader.build().urls(urls).parent(baseLoader.getParent())) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.equals(clazz.getName())) {
          return super.loadClass(name, resolve);
        }
        else {
          try {
            return baseLoader.loadClass(name);
          }
          catch (ClassNotFoundException e) {
            return super.loadClass(name, resolve);
          }
        }
      }
    };
    //noinspection unchecked
    return (T)loader.loadClass(clazz.getName()).newInstance();
  }

  @Contract(value = "_, null -> false", pure=true)
  public static boolean isExternalSystemAwareModule(@NotNull ProjectSystemId systemId, @Nullable Module module) {
    return module != null && !module.isDisposed() && systemId.getId().equals(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY));
  }

  @Contract(value = "_, null -> false", pure=true)
  public static boolean isExternalSystemAwareModule(@NotNull String systemId, @Nullable Module module) {
    return module != null && !module.isDisposed() && systemId.equals(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY));
  }

  @Nullable
  @Contract(pure=true)
  public static String getExternalProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY) : null;
  }

  @Nullable
  @Contract(pure=true)
  public static String getExternalRootProjectPath(@Nullable Module module) {
    return module != null && !module.isDisposed() ? module.getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY) : null;
  }

  @Nullable
  @Contract(pure=true)
  public static String getExternalProjectId(@Nullable Module module) {
    return module != null && !module.isDisposed() ? module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_ID_KEY) : null;
  }

  @Nullable
  @Contract(pure=true)
  public static String getExternalProjectGroup(@Nullable Module module) {
    return module != null && !module.isDisposed() ? module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_GROUP_KEY) : null;
  }

  @Nullable
  @Contract(pure=true)
  public static String getExternalProjectVersion(@Nullable Module module) {
    return module != null && !module.isDisposed() ? module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_VERSION_KEY) : null;
  }

  public static void subscribe(@NotNull Project project,
                               @NotNull ProjectSystemId systemId,
                               @NotNull ExternalSystemSettingsListener listener) {
    //noinspection unchecked
    getSettings(project, systemId).subscribe(listener);
  }
}
