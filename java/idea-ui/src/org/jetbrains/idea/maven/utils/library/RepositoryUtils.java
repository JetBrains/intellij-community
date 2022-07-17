// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.ide.JavaUiBundle;
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
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
import java.util.stream.Collectors;

public final class RepositoryUtils {
  private static final Logger LOG = Logger.getInstance(RepositoryUtils.class);

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

  public static boolean libraryHasExternalAnnotations(@Nullable LibraryEditor libraryEditor) {
    return libraryEditor != null && libraryEditor.getUrls(AnnotationOrderRootType.getInstance()).length > 0;
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
    return Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
  }

  public static Promise<List<OrderRoot>> loadDependenciesToLibrary(@NotNull final Project project,
                                                                   @NotNull final LibraryEx library,
                                                                   boolean downloadSources,
                                                                   boolean downloadJavaDocs,
                                                                   @Nullable String copyTo) {
    if (library.getKind() != RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) {
      return Promises.resolvedPromise(Collections.emptyList());
    }
    final RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    String[] annotationUrls = library.getUrls(AnnotationOrderRootType.getInstance());
    String[] excludedRootUrls = library.getExcludedRootUrls();

    return JarRepositoryManager.loadDependenciesAsync(
      project, properties, downloadSources, downloadJavaDocs, null, copyTo).thenAsync(roots -> {
      AsyncPromise<List<OrderRoot>> promise = new AsyncPromise<>();
      ApplicationManager.getApplication().invokeLater(
        roots == null || roots.isEmpty() ?
        () -> {
          String message = JavaUiBundle.message("notification.content.no.files.were.downloaded", properties.getMavenId());
          Notifications.Bus.notify(
            JarRepositoryManager.GROUP.createNotification(
                JavaUiBundle.message("notification.title.repository.library.synchronization"),
                message, NotificationType.ERROR),
            project);
          promise.setError(message);
        } :
        () -> {
          if (!library.isDisposed()) {
            LOG.debug("Loaded dependencies for '" + properties.getMavenId() + "' repository library");
            WriteAction.run(() -> {
              final NewLibraryEditor editor = new NewLibraryEditor(null, properties);
              editor.setKeepInvalidUrls(false);
              editor.removeAllRoots();
              editor.addRoots(roots);
              for (String url : annotationUrls) {
                editor.addRoot(url, AnnotationOrderRootType.getInstance());
              }
              List<String> allRootUrls = editor.getOrderRootTypes().stream()
                                               .flatMap(type -> Arrays.stream(editor.getUrls(type)))
                                               .collect(Collectors.toList());
              for (String excludedRootUrl: excludedRootUrls) {
                if (VfsUtilCore.isUnder(excludedRootUrl, allRootUrls)) {
                  editor.addExcludedRoot(excludedRootUrl);
                }
              }
              final LibraryEx.ModifiableModelEx model = library.getModifiableModel();
              editor.applyTo(model);
              model.commit();
            });
          }
          promise.setResult(roots);
        });
      return promise;
    });
  }

  public static Promise<List<OrderRoot>> reloadDependencies(@NotNull final Project project, @NotNull final LibraryEx library) {
    return loadDependenciesToLibrary(project, library, libraryHasSources(library), libraryHasJavaDocs(library), getStorageRoot(library, project));
  }
}
