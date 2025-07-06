// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarHttpDownloaderJps;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RepositoryLibrarySynchronizerKt;
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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  public static String getStorageRoot(@NotNull Project project, Library library) {
    return getStorageRoot(project, library.getUrls(OrderRootType.CLASSES));
  }


  /**
   * Returns the common storage root directory path (system-dependent) from a list of URLs.
   * <p>
   * If roots (non-filename part) of URLs are different, returns null.
   * On empty list also returns null.
   * <p>
   * Used by JarRepositoryManager to determine whether it needs to copy resolved files somewhere or now
   * (it's two modes of JPS repository libraries)
   * <p>
   */
  public static String getStorageRoot(Project project, String[] urls) {
    if (urls.length == 0) {
      return null;
    }

    String firstPath = getOnDiskParentPath(urls[0]);
    for (String root : urls) {
      if (!FileUtil.pathsEqual(firstPath, getOnDiskParentPath(root))) {
        return null;
      }
    }

    if (urls.length == 1) {
      // IJPL-175157 Only one file in the library, so we can't decide on storage root without looking into cache location
      // It's worse with symlinks where we may have a non-canonical path in `firstPath`
      // and canonical path in JarRepositoryManager.getLocalRepositoryPath
      var localRepositoryPath = JarRepositoryManager.getJPSLocalMavenRepositoryForIdeaProject(project).toFile();

      // happy case, no symlinks, so canonical localRepositoryPath is the same is firstPath
      if (FileUtil.startsWith(firstPath, localRepositoryPath.getPath())) {
        return null;
      }

      // non-happy case, symlinks, let's try to get as much canonical as we can.
      // covered by tests
      try {
        var canonicalFirstPath = new File(firstPath).getCanonicalPath();
        var canonicalLocalRepositoryPath = localRepositoryPath.getCanonicalPath();
        if (FileUtil.startsWith(canonicalFirstPath, canonicalLocalRepositoryPath)) {
          return null;
        }

        // let's try to support Windows too
        // https://bugs.openjdk.org/browse/JDK-8003887
        // File.getCanonicalFile() does not resolve symlinks on MS Windows
        var existingNioFirstPath = Path.of(canonicalFirstPath);
        while (existingNioFirstPath != null && !Files.exists(existingNioFirstPath)) {
          existingNioFirstPath = existingNioFirstPath.getParent();
        }
        if (existingNioFirstPath != null) {
          var existingNioCanonicalFirstPath = existingNioFirstPath.toRealPath().toString();
          if (FileUtil.startsWith(existingNioCanonicalFirstPath, canonicalLocalRepositoryPath)) {
            return null;
          }
        }
      }
      catch (IOException ignored) {
        // IOError, can't decide
      }
    }

    return firstPath;
  }

  private static String getOnDiskParentPath(String url) {
    String trimmedStart;

    if (url.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
      trimmedStart = url.substring(JarFileSystem.PROTOCOL_PREFIX.length());
    }
    else if (url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
      trimmedStart = url.substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length());
    }
    else {
      trimmedStart = url;
    }

    return FileUtil.toSystemDependentName(PathUtil.getParentPath(trimmedStart));
  }

  public static Promise<List<OrderRoot>> loadDependenciesToLibrary(final @NotNull Project project,
                                                                   final @NotNull LibraryEx library,
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
          LOG.debug("Finished setup library root for: " + library.getName());
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
            }
            catch (Throwable t) {
              LOG.warn("Unable to update project model for library '" + library.getName() + "'", t);
              result.setError(t);
            }
            finally {
              LOG.debug("Finished setup library root for: " + library.getName());
            }
          });

        return result;
      });
  }

  private static @NotNull Boolean libraryRootsEqual(@NotNull LibraryEx library,
                                                    String[] annotationUrls,
                                                    String[] excludedRootUrls,
                                                    List<OrderRoot> roots) {
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

  public static Promise<?> reloadDependencies(final @NotNull Project project, final @NotNull LibraryEx library) {
    if (JarHttpDownloaderJps.enabled() && isLibraryHasFixedVersion(library)) {
      Promise<?> promise = JarHttpDownloaderJps.getInstance(project).downloadLibraryFilesAsync(library);

      // callers of this function typically do not log, so do it for them
      promise.onError(error -> {
        LOG.warn("Failed to download repository library '" + library.getName() + "' with JarHttpDownloader", error);
      });

      if (LOG.isDebugEnabled()) {
        promise.onSuccess(result -> {
          LOG.debug("Downloaded repository library '" + library.getName() + "' with JarHttpDownloader");
        });
      }

      return promise;
    }

    Promise<List<OrderRoot>> mavenResolverPromise = loadDependenciesToLibrary(
      project, library, libraryHasSources(library), libraryHasJavaDocs(library), getStorageRoot(project, library));
    // callers of this function typically do not log, so do it for them
    mavenResolverPromise.onError(error -> {
      LOG.warn("Failed to download repository library '" + library.getName() + "' with maven resolver", error);
    });

    return mavenResolverPromise;
  }

  private static boolean isLibraryHasFixedVersion(final @NotNull LibraryEx library) {
    if (library.getKind() != RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) {
      return false;
    }

    RepositoryLibraryProperties libraryProperties = (RepositoryLibraryProperties) library.getProperties();
    if (libraryProperties == null) {
      return false;
    }

    return RepositoryLibrarySynchronizerKt.isLibraryHasFixedVersion(libraryProperties);
  }

  public static Promise<?> deleteAndReloadDependencies(final @NotNull Project project,
                                                       final @NotNull LibraryEx library) throws IOException {
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
