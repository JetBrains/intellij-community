// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerStore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.isDirectory;
import static java.util.Collections.emptySet;

public class JavaHomeFinderBasic {
  @SuppressWarnings("NonConstantLogger") private final Logger log = Logger.getInstance(getClass());
  private final List<Supplier<Set<String>>> myFinders = new ArrayList<>();

  JavaHomeFinderBasic(boolean forceEmbeddedJava, String... paths) {
    this(true, forceEmbeddedJava, paths);
  }

  JavaHomeFinderBasic(boolean checkDefaults, boolean forceEmbeddedJava, String... paths) {
    if (checkDefaults) {
      myFinders.add(this::checkDefaultLocations);
    }

    myFinders.add(this::findInPATH);
    myFinders.add(() -> findInSpecifiedPaths(paths));
    myFinders.add(this::findJavaInstalledBySdkMan);
    myFinders.add(this::findJavaInstalledByAsdfJava);
    myFinders.add(this::findJavaInstalledByGradle);

    if (!(this instanceof JavaHomeFinderWsl) && (forceEmbeddedJava || Registry.is("java.detector.include.embedded", false))) {
      myFinders.add(() -> scanAll(getJavaHome(), false));
    }
  }

  private @NotNull Set<String> findInSpecifiedPaths(String[] paths) {
    return scanAll(ContainerUtil.map(paths, Paths::get), true);
  }

  protected void registerFinder(@NotNull Supplier<Set<String>> finder) {
    myFinders.add(finder);
  }

  @NotNull
  public final Set<String> findExistingJdks() {
    Set<String> result = new TreeSet<>();

    for (Supplier<Set<String>> action : myFinders) {
      try {
        result.addAll(action.get());
      }
      catch (Exception e) {
        log.warn("Failed to find Java Home. " + e.getMessage(), e);
      }
    }

    return result;
  }

  private @NotNull Set<String> findInPATH() {
    try {
      String pathVarString = getEnvironmentVariable("PATH");
      if (pathVarString == null || pathVarString.isEmpty()) {
        return emptySet();
      }

      Set<Path> dirsToCheck = new HashSet<>();
      for (String p : pathVarString.split(File.pathSeparator)) {
        Path dir = Paths.get(p);
        if (!StringUtilRt.equal(dir.getFileName().toString(), "bin", SystemInfoRt.isFileSystemCaseSensitive)) {
          continue;
        }

        Path parentFile = dir.getParent();
        if (parentFile == null) {
          continue;
        }

        dirsToCheck.addAll(listPossibleJdkInstallRootsFromHomes(parentFile));
      }

      return scanAll(dirsToCheck, false);
    }
    catch (Exception e) {
      log.warn("Failed to scan PATH for JDKs. " + e.getMessage(), e);
      return emptySet();
    }
  }

  protected @Nullable String getEnvironmentVariable(@NotNull String name) {
    return EnvironmentUtil.getValue(name);
  }

  private @NotNull Set<String> checkDefaultLocations() {
    if (ApplicationManager.getApplication() == null) {
      return emptySet();
    }

    Set<Path> paths = new HashSet<>();
    paths.add(JdkInstaller.getInstance().defaultInstallDir());
    paths.addAll(JdkInstallerStore.getInstance().listJdkInstallHomes());

    for (Sdk jdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (!(jdk.getSdkType() instanceof JavaSdkType) || jdk.getSdkType() instanceof DependentSdkType) {
        continue;
      }

      String homePath = jdk.getHomePath();
      if (homePath == null) {
        continue;
      }

      paths.addAll(listPossibleJdkInstallRootsFromHomes(Paths.get(homePath)));
    }

    return scanAll(paths, true);
  }

  protected @NotNull Set<String> scanAll(@Nullable Path file, boolean includeNestDirs) {
    if (file == null) {
      return emptySet();
    }
    return scanAll(Collections.singleton(file), includeNestDirs);
  }

  protected @NotNull Set<String> scanAll(@NotNull Collection<? extends Path> files, boolean includeNestDirs) {
    Set<String> result = new HashSet<>();
    for (Path root : new HashSet<>(files)) {
      scanFolder(root, includeNestDirs, result);
    }
    return result;
  }

  protected void scanFolder(@NotNull Path folder, boolean includeNestDirs, @NotNull Collection<? super String> result) {
    if (JdkUtil.checkForJdk(folder)) {
      result.add(folder.toAbsolutePath().toString());
      return;
    }

    if (!includeNestDirs) return;
    File[] files = folder.toFile().listFiles();
    if (files == null) return;

    for (File candidate : files) {
      for (File adjusted : listPossibleJdkHomesFromInstallRoot(candidate)) {
        scanFolder(adjusted.toPath(), false, result);
      }
    }
  }

  @NotNull
  protected List<File> listPossibleJdkHomesFromInstallRoot(@NotNull File file) {
    return Collections.singletonList(file);
  }

  protected @NotNull List<Path> listPossibleJdkInstallRootsFromHomes(@NotNull Path file) {
    return Collections.singletonList(file);
  }

  private static @Nullable Path getJavaHome() {
    Path javaHome = Path.of(SystemProperties.getJavaHome());
    return isDirectory(javaHome) ? javaHome : null;
  }

  /**
   * Finds Java home directories installed by SDKMAN: https://github.com/sdkman
   */
  private @NotNull Set<@NotNull String> findJavaInstalledBySdkMan() {
    try {
      Path candidatesDir = findSdkManCandidatesDir();
      if (candidatesDir == null) return emptySet();
      Path javasDir = candidatesDir.resolve("java");
      if (!isDirectory(javasDir)) return emptySet();
      //noinspection UnnecessaryLocalVariable
      var homes = listJavaHomeDirsInstalledBySdkMan(javasDir);
      return homes;
    }
    catch (Exception e) {
      log.warn("Unexpected exception while looking for Sdkman directory: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      return emptySet();
    }
  }

  private @NotNull Set<String> findJavaInstalledByGradle() {
    Path jdks = getPathInUserHome(".gradle/jdks");
    return jdks != null && isDirectory(jdks) ? scanAll(jdks, true) : emptySet();
  }

  @Nullable
  private Path findSdkManCandidatesDir() {
    // first, try the special environment variable
    String candidatesPath = getEnvironmentVariable("SDKMAN_CANDIDATES_DIR");
    if (candidatesPath != null) {
      Path candidatesDir = Path.of(candidatesPath);
      if (isDirectory(candidatesDir)) {
        return candidatesDir;
      }
    }

    // then, try to use its 'primary' variable
    String primaryPath = getEnvironmentVariable("SDKMAN_DIR");
    if (primaryPath != null) {
      Path candidatesDir = Path.of(primaryPath, "candidates");
      if (isDirectory(candidatesDir)) {
        return candidatesDir;
      }
    }

    // finally, try the usual location in UNIX
    if (!SystemInfo.isWindows || this instanceof JavaHomeFinderWsl) {
      Path candidates = getPathInUserHome(".sdkman/candidates");
      if (candidates != null && isDirectory(candidates)) {
        return candidates;
      }
    }

    // no chances
    return null;
  }

  protected @Nullable Path getPathInUserHome(@NotNull String relativePath) {
    String homePath = System.getProperty("user.home");
    return homePath != null ? Path.of(homePath, relativePath) : null;
  }

  private @NotNull Set<@NotNull String> listJavaHomeDirsInstalledBySdkMan(@NotNull Path javasDir) {
    var mac = SystemInfo.isMac;
    var result = new HashSet<@NotNull String>();

    try (Stream<Path> stream = Files.list(javasDir)) {
      List<Path> innerDirectories = stream.filter(d -> isDirectory(d)).collect(Collectors.toList());
      for (Path innerDir: innerDirectories) {
        var home = innerDir;
        var releaseFile = home.resolve("release");
        if (!safeExists(releaseFile)) continue;

        if (mac) {
          // Zulu JDK on MacOS has a rogue layout, with which Gradle failed to operate (see the bugreport IDEA-253051),
          // and in order to get Gradle working with Zulu JDK we should use it's second home (when symbolic links are resolved).
          try {
            if (Files.isSymbolicLink(releaseFile)) {
              var realReleaseFile = releaseFile.toRealPath();
              if (!safeExists(realReleaseFile)) {
                log.warn("Failed to resolve the target file (it doesn't exist) for: " + releaseFile);
                continue;
              }
              var realHome = realReleaseFile.getParent();
              if (realHome == null) {
                log.warn("Failed to resolve the target file (it has no parent dir) for: " + releaseFile);
                continue;
              }
              home = realHome;
            }
          }
          catch (IOException ioe) {
            log.warn("Failed to resolve the target file for: " + releaseFile + ": " + ioe.getMessage());
            continue;
          }
          catch (Exception e) {
            log.warn("Failed to resolve the target file for: " +
                     releaseFile + ": Unexpected exception " + e.getClass().getSimpleName() + ": " + e.getMessage());
            continue;
          }
        }

        result.add(home.toString());
      }
    }
    catch (IOException ioe) {
      log.warn("I/O exception while listing Java home directories installed by Sdkman: "+ioe.getMessage(), ioe);
      return emptySet();
    }
    catch (Exception e) {
      log.warn("Unexpected exception while listing Java home directories installed by Sdkman: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      return emptySet();
    }

    return result;
  }


  /**
   * Finds Java home directories installed by asdf-java: https://github.com/halcyon/asdf-java
   */
  private @NotNull Set<String> findJavaInstalledByAsdfJava() {
    File installsDir = findAsdfInstallsDir();
    if (installsDir == null) return Collections.emptySet();
    File javasDir = new File(installsDir, "java");
    return safeIsDirectory(javasDir) ? scanAll(javasDir.toPath(), true) : Collections.emptySet();
  }

  @Nullable
  private File findAsdfInstallsDir() {
    // try to use environment variable for custom data directory
    // https://asdf-vm.com/#/core-configuration?id=environment-variables
    String dataDir = EnvironmentUtil.getValue("ASDF_DATA_DIR");
    if (dataDir != null) {
      File primaryDir = new File(dataDir);
      if (primaryDir.isDirectory()) {
        File installsDir = primaryDir.toPath().resolve("installs").toFile();
        if (safeIsDirectory(installsDir)) return installsDir;
      }
    }

    // finally, try the usual location in Unix or MacOS
    if (!SystemInfo.isWindows) {
      String homePath = System.getProperty("user.home");
      if (homePath != null) {
        File homeDir = new File(homePath);
        File installsDir = homeDir.toPath().resolve(".asdf").resolve("installs").toFile();
        if (safeIsDirectory(installsDir)) return installsDir;
      }
    }

    // no chances
    return null;
  }


  private boolean safeIsDirectory(@NotNull File dir) {
    try {
      return dir.isDirectory();
    }
    catch (SecurityException se) {
      return false; // when a directory is not accessible we should ignore it
    }
    catch (Exception e) {
      log.debug("Failed to check directory existence: unexpected exception " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      return false;
    }
  }

  private boolean safeExists(@NotNull Path path) {
    try {
      return Files.exists(path);
    }
    catch (Exception e) {
      log.debug("Failed to check file existence: unexpected exception " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      return false;
    }
  }
}
