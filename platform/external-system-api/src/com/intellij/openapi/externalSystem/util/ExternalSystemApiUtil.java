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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 4/1/13 1:31 PM
 */
public class ExternalSystemApiUtil {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemApiUtil.class.getName());

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
    return PathUtil.getCanonicalPath(new File(path).getAbsolutePath());
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

  @Nullable
  public static ExternalSystemManager<?, ?, ?, ?> getManager(@NotNull ProjectSystemId externalSystemId) {
    return MANAGERS.getValue().get(externalSystemId);
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
            task.run();
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
}
