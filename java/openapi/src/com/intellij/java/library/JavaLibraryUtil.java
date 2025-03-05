// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library;

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.Interner;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.Attributes;

import static com.intellij.java.library.JavaLibraryUtil.matchLibraryName;
import static com.intellij.openapi.util.text.StringUtil.isDecimalDigit;
import static com.intellij.psi.search.GlobalSearchScope.allScope;
import static com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope;
import static java.util.Collections.emptyMap;

/**
 * Checks the presence of JVM library classes in {@link Module} and {@link Project} dependencies.
 * Should be used for short-circuit checks to enable/disable IDE functionality depending on relevant libraries.
 */
public final class JavaLibraryUtil {

  private JavaLibraryUtil() {
  }

  private static final Key<CachedValue<Map<String, Boolean>>> LIBRARY_CLASSES_PRESENCE_KEY = Key.create("LIBRARY_CLASSES_PRESENCE");

  private static final Key<ParameterizedCachedValue<Libraries, Module>> MAVEN_LIBRARIES_MODULE_KEY = Key.create("MAVEN_LIBRARY_PRESENCE");

  private static final ParameterizedCachedValueProvider<Libraries, Module> MAVEN_LIBRARIES_MODULE_PROVIDER = module -> {
    String externalSystemId = ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId();
    Libraries libraries = fillLibraries(OrderEnumerator.orderEntries(module),
                                        isUnsupportedBuildSystem(externalSystemId));
    return Result.create(libraries, ProjectRootManager.getInstance(module.getProject()));
  };

  private static final Interner<String> INTERNER = Interner.createStringInterner();

  private static synchronized @NotNull String intern(@NotNull String str) {
    // interner is not thread-safe
    return INTERNER.intern(str);
  }

  public static @Nullable MavenCoordinates getMavenCoordinates(@NotNull Library library) {
    if (library instanceof LibraryEx) {
      LibraryProperties<?> libraryProperties = ((LibraryEx)library).getProperties();
      if (libraryProperties instanceof LibraryWithMavenCoordinatesProperties) {
        return ((LibraryWithMavenCoordinatesProperties)libraryProperties).getMavenCoordinates();
      }
    }

    ProgressManager.checkCanceled();

    var name = library.getName();
    if (name == null) return null;

    var coordinatesString = StringUtil.substringAfter(name, ": ");
    if (coordinatesString == null) return null;

    var parts = StringUtil.split(coordinatesString, ":");
    if (parts.size() < 3) return null;

    return new MavenCoordinates(intern(parts.get(0)),
                                intern(parts.get(1)),
                                intern(parts.get(parts.size() - 1)));
  }

  /**
   * Checks if the passed library class is available in the project. Should be used only with constant class names from a known library.
   * Returns false from dumb mode or if the project is disposed.
   */
  @RequiresReadLock
  public static boolean hasLibraryClass(@Nullable Project project, @NotNull String classFqn) {
    if (project == null || project.isDisposed()) return false;
    if (project.isDefault()) return false; // EA-396106
    return getLibraryClassMap(project).getOrDefault(classFqn, false);
  }

  /**
   * Checks if the passed library class is available in the module. Should be used only with constant class names from a known library.
   * Returns false from dumb mode or if the module is already disposed.
   */
  @RequiresReadLock
  public static boolean hasLibraryClass(@Nullable Module module, @NotNull String classFqn) {
    if (module == null || module.isDisposed()) return false;
    if (module.getProject().isDefault()) return false; // EA-396106
    return getLibraryClassMap(module).getOrDefault(classFqn, false);
  }

  private static Map<String, Boolean> getLibraryClassMap(@NotNull Project project) {
    if (DumbService.isDumb(project)) return emptyMap();

    return CachedValuesManager.getManager(project).getCachedValue(project, LIBRARY_CLASSES_PRESENCE_KEY, () -> {
      ConcurrentMap<String, Boolean> map = ConcurrentFactoryMap.createMap(classFqn -> {
        return JavaPsiFacade.getInstance(project).hasClass(classFqn, allScope(project));
      });
      return createResultWithDependencies(map, project);
    }, false);
  }

  private static Map<String, Boolean> getLibraryClassMap(@NotNull Module module) {
    if (DumbService.isDumb(module.getProject())) return emptyMap();

    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, LIBRARY_CLASSES_PRESENCE_KEY, () -> {
      Project project = module.getProject();
      ConcurrentMap<String, Boolean> map = ConcurrentFactoryMap.createMap(classFqn -> {
        return JavaPsiFacade.getInstance(project).hasClass(classFqn, moduleWithDependenciesAndLibrariesScope(module));
      });
      return createResultWithDependencies(map, project);
    }, false);
  }

  private static Result<Map<String, Boolean>> createResultWithDependencies(@NotNull Map<String, Boolean> map, @NotNull Project project) {
    return Result.create(map, JavaLibraryModificationTracker.getInstance(project));
  }

  @RequiresReadLock
  public static boolean hasLibraryJar(@Nullable Project project, @NotNull String mavenCoords) {
    if (project == null || project.isDisposed()) return false;
    if (project.isDefault()) return false; // EA-396106

    return getProjectLibraries(project).contains(mavenCoords);
  }

  @RequiresReadLock
  public static boolean hasAnyLibraryJar(@Nullable Project project, @NotNull Collection<String> mavenCoords) {
    if (project == null || project.isDisposed()) return false;
    if (project.isDefault()) return false; // EA-396106

    Libraries libraries = getProjectLibraries(project);
    for (String coord : mavenCoords) {
      if (libraries.contains(coord)) return true;
    }

    return false;
  }

  @RequiresReadLock
  public static boolean hasLibraryJar(@Nullable Module module, @NotNull String mavenCoords) {
    if (module == null || module.isDisposed()) return false;
    if (module.getProject().isDefault()) return false; // EA-396106

    return getModuleLibraries(module).contains(mavenCoords);
  }

  @RequiresReadLock
  public static boolean hasAnyLibraryJar(@Nullable Module module, @NotNull Collection<String> mavenCoords) {
    if (module == null || module.isDisposed()) return false;
    if (module.getProject().isDefault()) return false; // EA-396106

    Libraries libraries = getModuleLibraries(module);
    for (String coord : mavenCoords) {
      if (libraries.contains(coord)) return true;
    }

    return false;
  }

  @RequiresReadLock
  public static @Nullable String getLibraryVersion(@NotNull Module module, @NotNull String mavenCoords) {
    return getLibraryVersion(module, mavenCoords, null);
  }

  @RequiresReadLock
  public static @Nullable String getLibraryVersion(@NotNull Module module,
                                                   @NotNull String mavenCoords,
                                                   @Nullable Attributes.Name versionAttribute) {
    String externalSystemId = ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId();
    if (isUnsupportedBuildSystem(externalSystemId)) {
      return getJpsLibraryVersion(module, mavenCoords, versionAttribute);
    }
    else {
      return getExternalSystemLibraryVersion(module, mavenCoords);
    }
  }

  private static @Nullable String getJpsLibraryVersion(@NotNull Module module,
                                                       @NotNull String mavenCoords,
                                                       @Nullable Attributes.Name versionAttribute) {
    String name = StringUtil.substringAfter(mavenCoords, ":");
    if (name == null) return null;

    Ref<String> result = new Ref<>();
    OrderEnumerator.orderEntries(module).recursively()
      .forEachLibrary(library -> {
        VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
        for (VirtualFile libraryFile : libraryFiles) {
          if (matchLibraryName(sanitizeLibraryName(libraryFile.getNameWithoutExtension()), name)) {
            VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(libraryFile);
            if (jarFile == null) continue;

            String version = JarUtil.getJarAttribute(VfsUtilCore.virtualToIoFile(jarFile), Attributes.Name.IMPLEMENTATION_VERSION);
            if (version == null && versionAttribute != null) {
              version = JarUtil.getJarAttribute(VfsUtilCore.virtualToIoFile(jarFile), versionAttribute);
            }
            if (version != null) {
              result.set(version);
              return false;
            }
          }
        }
        return true;
      });
    return result.get();
  }

  private static @Nullable String getExternalSystemLibraryVersion(@NotNull Module module, @NotNull String mavenCoords) {
    Ref<String> result = new Ref<>();
    OrderEnumerator.orderEntries(module).recursively()
      .forEachLibrary(library -> {
        MavenCoordinates coordinates = getMavenCoordinates(library);
        if (coordinates != null) {
          String location = coordinates.getGroupId() + ":" + coordinates.getArtifactId();
          if (location.equals(mavenCoords)) {
            result.set(coordinates.getVersion());
            return false;
          }
        }
        return true;
      });
    return result.get();
  }

  private static @NotNull Libraries getProjectLibraries(@NotNull Project project) {
    return JavaLibraryHolder.getInstance(project).getLibraries();
  }

  private static @NotNull Libraries getModuleLibraries(@NotNull Module module) {
    return CachedValuesManager.getManager(module.getProject())
      .getParameterizedCachedValue(module, MAVEN_LIBRARIES_MODULE_KEY, MAVEN_LIBRARIES_MODULE_PROVIDER, false, module);
  }

  private static boolean isUnsupportedBuildSystem(@Nullable String externalSystemId) {
    return externalSystemId == null || "Blaze".equals(externalSystemId);
  }

  static @NotNull Libraries fillLibraries(OrderEnumerator orderEnumerator, boolean collectFiles) {
    Set<String> allMavenCoords = new HashSet<>();
    Map<String, String> jarLibrariesIndex = new HashMap<>();

    orderEnumerator.recursively()
      .forEachLibrary(library -> {
        MavenCoordinates coordinates = getMavenCoordinates(library);
        if (coordinates != null
            && allMavenCoords.add(intern(coordinates.getGroupId() + ":" + coordinates.getArtifactId()))) {
          ProgressManager.checkCanceled();
        }

        if (collectFiles && library instanceof LibraryEx) {
          LibraryProperties<?> libraryProperties = ((LibraryEx)library).getProperties();
          if (libraryProperties == null || libraryProperties instanceof RepositoryLibraryProperties) {
            collectFiles(library, coordinates, jarLibrariesIndex);
          }
        }

        return true;
      });

    return new Libraries(Set.copyOf(allMavenCoords), Map.copyOf(jarLibrariesIndex));
  }

  private static void collectFiles(@NotNull Library library,
                                   @Nullable MavenCoordinates coordinates,
                                   @NotNull Map<String, String> jarLibrariesIndex) {
    ProgressManager.checkCanceled();

    VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
    if (coordinates == null || libraryFiles.length > 1) {
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();

      for (VirtualFile libraryFile : libraryFiles) {
        if (libraryFile.getFileSystem() != jarFileSystem) continue;

        ProgressManager.checkCanceled();

        String nameWithoutExtension = libraryFile.getNameWithoutExtension();

        // Drop prefix of Bazel processed libraries IDEA-324807
        nameWithoutExtension = intern(sanitizeLibraryName(nameWithoutExtension));

        jarLibrariesIndex.put(nameWithoutExtension, nameWithoutExtension);

        String[] nameParts = nameWithoutExtension.split("-");
        StringBuilder nameBuilder = new StringBuilder();

        for (int i = 0; i < nameParts.length; i++) {
          String part = nameParts[i];
          if (!part.isEmpty() && isDecimalDigit(part.charAt(0))) {
            break;
          }

          if (i > 0) {
            nameBuilder.append("-");
          }
          nameBuilder.append(part);
        }

        String indexNamePart = nameBuilder.toString();
        if (!indexNamePart.equals(nameWithoutExtension)) {
          jarLibrariesIndex.put(indexNamePart, intern(nameWithoutExtension));
        }
      }
    }
  }

  private static final List<String> BAZEL_PREFIXES = List.of("processed_", "header_");

  private static @NotNull String sanitizeLibraryName(@NotNull String nameWithoutExtension) {
    var name = nameWithoutExtension;
    for (String prefix : BAZEL_PREFIXES) {
      name = StringsKt.removePrefix(name, prefix);
    }
    return name; // omit this prefix for Bazel
  }

  static boolean matchLibraryName(@NotNull String fileName, @NotNull String libraryName) {
    if (fileName.equals(libraryName)) return true;

    String prefix = libraryName + "-";
    if (!fileName.startsWith(prefix)) return false;

    if (fileName.length() == prefix.length()) return false;

    char versionStart = fileName.charAt(prefix.length());
    return isDecimalDigit(versionStart);
  }
}

record Libraries(Set<String> mavenLibraries,
                 Map<String, String> jpsNameIndex) {
  boolean contains(@NotNull String mavenCoords) {
    if (mavenLibraries.contains(mavenCoords)) return true;
    if (jpsNameIndex.isEmpty()) return false;

    String libraryName = getLibraryName(mavenCoords);
    if (libraryName == null) return false;

    String existingJpsLibraryName = jpsNameIndex.get(libraryName);
    if (existingJpsLibraryName == null) return false;

    return matchLibraryName(existingJpsLibraryName, libraryName);
  }

  private static @Nullable String getLibraryName(@NotNull String mavenCoords) {
    return StringUtil.substringAfter(mavenCoords, ":");
  }
}