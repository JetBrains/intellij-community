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
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalEntity;
import com.intellij.openapi.externalSystem.model.project.ExternalEntityVisitor;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.manage.GradleProjectEntityChangeListener;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 4/1/13 1:31 PM
 */
public class ExternalSystemUtil {

  @NotNull public static final String PATH_SEPARATOR = "/";

  @NotNull private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(?:.*/)?(.+?)(?:-([\\d+](?:\\.[\\d]+)*))?(?:\\.[^\\.]+?)?");

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

  public static void dispatch(@Nullable Object entity, @NotNull ExternalEntityVisitor gradleVisitor, @NotNull IdeEntityVisitor ideVisitor) {
    if (entity instanceof ExternalEntity) {
      ((ExternalEntity)entity).invite(gradleVisitor);
    }
    else {
      dispatch(entity, ideVisitor);
    }
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
  public static ProjectSystemId detectOwner(@Nullable ExternalEntity externalEntity, @Nullable Object ideEntity) {
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

  public static void executeProjectChangeAction(@NotNull Project project, @NotNull Object entityToChange, @NotNull Runnable task) {
    executeProjectChangeAction(project, entityToChange, false, task);
  }

  public static void executeProjectChangeAction(@NotNull Project project,
                                                @NotNull Object entityToChange,
                                                boolean synchronous,
                                                @NotNull Runnable task)
  {
    executeProjectChangeAction(project, Collections.singleton(entityToChange), synchronous, task);
  }

  public static void executeProjectChangeAction(@NotNull final Project project,
                                                @NotNull final Iterable<?> entitiesToChange,
                                                @NotNull final Runnable task)
  {
    executeProjectChangeAction(project, entitiesToChange, false, task);
  }

  public static void executeProjectChangeAction(@NotNull final Project project,
                                                @NotNull final Iterable<?> entitiesToChange,
                                                boolean synchronous,
                                                @NotNull final Runnable task)
  {
    Runnable wrappedTask = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final GradleProjectEntityChangeListener publisher = project.getMessageBus().syncPublisher(GradleProjectEntityChangeListener.TOPIC);
            for (Object e : entitiesToChange) {
              publisher.onChangeStart(e);
            }
            try {
              task.run();
            }
            finally {
              for (Object e : entitiesToChange) {
                publisher.onChangeEnd(e);
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
}
