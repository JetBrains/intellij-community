/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class RepositoryUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.utils.library.RepositoryUtils");

  public static boolean libraryHasSources(@Nullable Library library) {
    return library != null && library.getUrls(OrderRootType.SOURCES).length > 0;
  }

  public static boolean libraryHasSources(@Nullable LibraryEditor libraryEditor) {
    return libraryEditor != null && libraryEditor.getUrls(OrderRootType.SOURCES).length > 0;
  }

  public static boolean libraryHasJavaDocs(@Nullable Library library) {
    return library != null && library.getUrls(JavadocOrderRootType.getInstance()).length > 0;
  }

  public static boolean libraryHasJavaDocs(@Nullable LibraryEditor libraryEditor) {
    return libraryEditor != null && libraryEditor.getUrls(JavadocOrderRootType.getInstance()).length > 0;
  }

  public static String getStorageRoot(Library library, Project project) {
    return getStorageRoot(library.getUrls(OrderRootType.CLASSES), project);
  }


  public static String getStorageRoot(String[] urls, Project project) {
    if (urls.length == 0) {
      return null;
    }
    final String localRepositoryPath = FileUtil.toSystemIndependentName(JarRepositoryManager.getLocalRepositoryPath().getAbsolutePath());
    List<String> roots = JBIterable.of(urls).transform(urlWithPrefix -> {
      String url = StringUtil.trimStart(urlWithPrefix, JarFileSystem.PROTOCOL_PREFIX);
      return url.startsWith(localRepositoryPath) ? null : FileUtil.toSystemDependentName(PathUtil.getParentPath(url));
    }).toList();
    final Map<String, Integer> counts = new HashMap<>();
    for (String root : roots) {
      final Integer count = counts.get(root);
      counts.put(root, count != null ? count + 1 : 1);
    }
    return Collections.max(counts.entrySet(), Comparator.comparing(Map.Entry::getValue)).getKey();
  }

  /**
   * Aether-based implementation understands version specification in terms of "LATEST" and "RELEASE"
   */
  @Deprecated
  public static String resolveEffectiveVersion(@NotNull Project project, @NotNull RepositoryLibraryProperties properties) {
    String version = properties.getVersion();
    boolean isLatest = RepositoryLibraryDescription.LatestVersionId.equals(version);
    boolean isRelease = RepositoryLibraryDescription.ReleaseVersionId.equals(version);
    if (isLatest || isRelease) {
      try {
        final Collection<String> versions =  JarRepositoryManager.getAvailableVersions(
          project, RepositoryLibraryDescription.findDescription(properties.getGroupId(), properties.getArtifactId())
        ).get();
        for (String ver : versions) {
          if (!isRelease || !ver.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) {
            version = ver;
            break;
          }
        }
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.error("Got unexpected exception while resolving artifact versions", e);
      }
    }
    return version;
  }

  public static void loadDependencies(@NotNull final Project project,
                                      @NotNull final LibraryEx library,
                                      boolean downloadSources,
                                      boolean downloadJavaDocs,
                                      @Nullable String copyTo) {
    if (library.getKind() != RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) {
      return;
    }
    final RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    String[] annotationUrls = library.getUrls(AnnotationOrderRootType.getInstance());

    JarRepositoryManager.loadDependenciesAsync(
      project, properties, downloadSources, downloadJavaDocs, null, copyTo,
      roots -> {
        ApplicationManager.getApplication().invokeLater(
          roots == null || roots.isEmpty() ?
          () -> Notifications.Bus.notify(new Notification(
                "Repository", "Repository library synchronization", "No files were downloaded for " + properties.getMavenId(), NotificationType.ERROR
                ), project) :
          () -> {
            if (!library.isDisposed()) {
              WriteAction.run(() -> {
                final NewLibraryEditor editor = new NewLibraryEditor(null, properties);
                editor.setKeepInvalidUrls(false);
                editor.removeAllRoots();
                editor.addRoots(roots);
                for (String url : annotationUrls) {
                  editor.addRoot(url, AnnotationOrderRootType.getInstance());
                }
                final Library.ModifiableModel model = library.getModifiableModel();
                editor.applyTo((LibraryEx.ModifiableModelEx)model);
                model.commit();
              });
            }
          });
      }
    );
  }

  public static void reloadDependencies(@NotNull final Project project, @NotNull final LibraryEx library) {
    loadDependencies(project, library, libraryHasSources(library), libraryHasJavaDocs(library), getStorageRoot(library, project));
  }
}
