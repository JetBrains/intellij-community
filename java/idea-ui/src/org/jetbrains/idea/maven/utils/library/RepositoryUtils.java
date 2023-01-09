// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

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

  public static String getStorageRoot(Library library) {
    return getStorageRoot(library.getUrls(OrderRootType.CLASSES));
  }


  public static String getStorageRoot(String[] urls) {
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
      return resolvedPromise(Collections.emptyList());
    }
    final RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    String[] annotationUrls = library.getUrls(AnnotationOrderRootType.getInstance());
    String[] excludedRootUrls = library.getExcludedRootUrls();

    return JarRepositoryManager.loadDependenciesAsync(
      project, properties, downloadSources, downloadJavaDocs, null, copyTo).thenAsync(roots -> {

        if (roots == null || roots.isEmpty()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            RepositoryLibraryResolveErrorNotification.showOrUpdate(properties, project);
          });

          return rejectedPromise("Library '" + properties.getMavenId() + "' resolution failed");
        }
        else {
          LOG.debug("Loaded dependencies for '" + properties.getMavenId() + "' repository library");

          return setupLibraryRoots(library, properties, annotationUrls, excludedRootUrls, roots).then(ignored -> roots);
        }
    });
  }

  private static Promise<?> setupLibraryRoots(@NotNull LibraryEx library,
                                              RepositoryLibraryProperties properties,
                                              String[] annotationUrls,
                                              String[] excludedRootUrls,
                                              List<OrderRoot> roots) {
    return ReadAction.nonBlocking(() -> {
        if (library.isDisposed()) {
          return false;
        }

        return libraryRootsEqual(library, annotationUrls, excludedRootUrls, roots);
      })
      .submit(NonUrgentExecutor.getInstance())
      .thenAsync(rootsEqual -> {
        if (rootsEqual) {
          // Nothing to update
          return resolvedPromise();
        }

        AsyncPromise<?> result = new AsyncPromise<>();

        ApplicationManager.getApplication().invokeLaterOnWriteThread(
          () -> {
            try {
              ApplicationManager.getApplication().runWriteAction(() -> {
                if (library.isDisposed()) {
                  result.cancel();
                  return;
                }

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
                for (String excludedRootUrl : excludedRootUrls) {
                  if (VfsUtilCore.isUnder(excludedRootUrl, allRootUrls)) {
                    editor.addExcludedRoot(excludedRootUrl);
                  }
                }
                final LibraryEx.ModifiableModelEx model = library.getModifiableModel();
                editor.applyTo(model);
                model.commit();

                result.setResult(null);
              });
            } catch (Throwable t) {
              LOG.warn("Unable to update project model for library '" + library.getName() + "'", t);
              result.setError(t);
            }
          });

        return result;
      });
  }

  @NotNull
  private static Boolean libraryRootsEqual(@NotNull LibraryEx library, String[] annotationUrls, String[] excludedRootUrls, List<OrderRoot> roots) {
    List<String> allRootUrls = new ArrayList<>();

    Set<Pair<OrderRootType, String>> actualRoots = new HashSet<>();
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      for (String url : library.getUrls(rootType)) {
        actualRoots.add(new Pair<>(rootType, url));
        allRootUrls.add(url);
      }
    }

    Set<Pair<OrderRootType, String>> expectedRoots = new HashSet<>();
    for (OrderRoot root : roots) {
      // intentionally ignoring jar directory flag, it implausible for our case to change
      // from jar directory to jar file preserving url
      expectedRoots.add(new Pair<>(root.getType(), root.getFile().getUrl()));
    }
    for (String annotationUrl : annotationUrls) {
      expectedRoots.add(new Pair<>(AnnotationOrderRootType.getInstance(), annotationUrl));
    }

    if (!actualRoots.equals(expectedRoots)) {
      return false;
    }

    Set<String> expectedExcludedRootUrls = Arrays.stream(excludedRootUrls)
      .filter(excludedRootUrl -> VfsUtilCore.isUnder(excludedRootUrl, allRootUrls))
      .collect(Collectors.toSet());
    Set<String> actualExcludedRootUrls = Arrays.stream(library.getExcludedRootUrls()).collect(Collectors.toSet());
    if (!expectedExcludedRootUrls.equals(actualExcludedRootUrls)) {
      return false;
    }

    return true;
  }

  public static Promise<List<OrderRoot>> reloadDependencies(@NotNull final Project project, @NotNull final LibraryEx library) {
    return loadDependenciesToLibrary(project, library, libraryHasSources(library), libraryHasJavaDocs(library), getStorageRoot(library));
  }

  public static Promise<List<OrderRoot>> deleteAndReloadDependencies(@NotNull final Project project,
                                                                     @NotNull final LibraryEx library) throws IOException {
    LOG.debug("start deleting files in library " + library.getName());
    var filesToDelete = new ArrayList<VirtualFile>();
    for (var rootType : OrderRootType.getAllTypes()) {
      Collections.addAll(filesToDelete, library.getRootProvider().getFiles(rootType));
    }

    for (VirtualFile file : filesToDelete) {
      if (file.getFileSystem() instanceof ArchiveFileSystem archiveFs) {
        var local = archiveFs.getLocalByEntry(file);
        if (null != local) {
          var path = local.toNioPath();
          FileUtil.delete(path);
        }
      }
    }
    return reloadDependencies(project, library);
  }
}
