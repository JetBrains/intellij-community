// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerStore;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public class JavaHomeFinderBasic {
  @SuppressWarnings("NonConstantLogger") private final Logger log = Logger.getInstance(getClass());
  private final List<Supplier<? extends Set<String>>> myFinders = new ArrayList<>();
  private final JavaHomeFinder.SystemInfoProvider mySystemInfo;

  private boolean myCheckDefaultInstallDir = true;
  private boolean myCheckUsedInstallDirs = true;
  private boolean myCheckConfiguredJdks = true;

  private boolean myCheckEmbeddedJava = false;

  private @NotNull String @NotNull[] mySpecifiedPaths = ArrayUtil.EMPTY_STRING_ARRAY;

  public JavaHomeFinderBasic(@NotNull JavaHomeFinder.SystemInfoProvider systemInfo) {
    mySystemInfo = systemInfo;

    myFinders.add(this::checkDefaultLocations);
    myFinders.add(this::findInPATH);
    myFinders.add(this::findInJavaHome);
    myFinders.add(this::findInSpecifiedPaths);
    myFinders.add(this::findJavaInstalledBySdkMan);
    myFinders.add(this::findJavaInstalledByAsdfJava);
    myFinders.add(this::findJavaInstalledByGradle);
    myFinders.add(this::findJavaInstalledByMise);

    myFinders.add(
      () -> myCheckEmbeddedJava ? scanAll(getJavaHome(), false) : Collections.emptySet()
    );
  }

  public @NotNull JavaHomeFinderBasic checkDefaultInstallDir(boolean value) {
    myCheckDefaultInstallDir = value;
    return this;
  }

  public @NotNull JavaHomeFinderBasic checkUsedInstallDirs(boolean value) {
    myCheckUsedInstallDirs = value;
    return this;
  }

  public @NotNull JavaHomeFinderBasic checkConfiguredJdks(boolean value) {
    myCheckConfiguredJdks = value;
    return this;
  }

  public @NotNull JavaHomeFinderBasic checkEmbeddedJava(boolean value) {
    myCheckEmbeddedJava = value;
    return this;
  }

  public @NotNull JavaHomeFinderBasic checkSpecifiedPaths(String @NotNull ... paths) {
    mySpecifiedPaths = paths;
    return this;
  }

  protected JavaHomeFinder.SystemInfoProvider getSystemInfo() {
    return mySystemInfo;
  }

  private @NotNull Set<String> findInSpecifiedPaths() {
    return scanAll(ContainerUtil.map(mySpecifiedPaths, Paths::get), true);
  }

  protected void registerFinder(@NotNull Supplier<? extends Set<String>> finder) {
    myFinders.add(finder);
  }

  public final @NotNull Set<String> findExistingJdks() {
    Set<String> result = new TreeSet<>();

    for (Supplier<? extends Set<String>> action : myFinders) {
      try {
        result.addAll(action.get());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        log.warn("Failed to find Java Home. " + e.getMessage(), e);
      }
    }

    return result;
  }

  public @NotNull Set<String> findInJavaHome() {
    String javaHome = mySystemInfo.getEnvironmentVariable("JAVA_HOME");
    return javaHome != null ? scanAll(mySystemInfo.getPath(javaHome), false) : Collections.emptySet();
  }

  private @NotNull Set<String> findInPATH() {
    try {
      String pathVarString = mySystemInfo.getEnvironmentVariable("PATH");
      if (pathVarString == null || pathVarString.isEmpty()) {
        return Collections.emptySet();
      }

      Set<Path> dirsToCheck = new HashSet<>();
      for (String p : pathVarString.split(mySystemInfo.getPathSeparator())) {
        Path dir = mySystemInfo.getPath(p);
        if (!StringUtilRt.equal(dir.getFileName().toString(), "bin", mySystemInfo.isFileSystemCaseSensitive())) {
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
      return Collections.emptySet();
    }
  }

  private @NotNull Set<String> checkDefaultLocations() {
    if (ApplicationManager.getApplication() == null) {
      return Collections.emptySet();
    }

    Set<Path> paths = new HashSet<>();

    if (myCheckDefaultInstallDir) {
      paths.add(JdkInstaller.getInstance().defaultInstallDir());
    }

    if (myCheckUsedInstallDirs) {
      paths.addAll(JdkInstallerStore.Companion.getInstance().listJdkInstallHomes());
    }

    if (myCheckConfiguredJdks) {
      for (Sdk jdk : ProjectJdkTable.getInstance().getAllJdks()) {
        if (!(jdk.getSdkType() instanceof JavaSdkType) || jdk.getSdkType() instanceof DependentSdkType) {
          continue;
        }

        String homePath = jdk.getHomePath();
        if (homePath == null) {
          continue;
        }

        paths.addAll(listPossibleJdkInstallRootsFromHomes(mySystemInfo.getPath(homePath)));
      }
    }

    return scanAll(paths, true);
  }

  protected @NotNull Set<String> scanAll(@Nullable Path file, boolean includeNestDirs) {
    if (file == null) {
      return Collections.emptySet();
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
    try (Stream<Path> files = Files.list(folder)) {
      files.forEach(candidate -> {
        for (Path adjusted : listPossibleJdkHomesFromInstallRoot(candidate)) {
          scanFolder(adjusted, false, result);
        }
      });
    }
    catch (IOException ignore) {
    }
  }

  protected @NotNull List<Path> listPossibleJdkHomesFromInstallRoot(@NotNull Path path) {
    return Collections.singletonList(path);
  }

  protected @NotNull List<Path> listPossibleJdkInstallRootsFromHomes(@NotNull Path file) {
    return Collections.singletonList(file);
  }

  private static @Nullable Path getJavaHome() {
    Path javaHome = Path.of(SystemProperties.getJavaHome());
    return Files.isDirectory(javaHome) ? javaHome : null;
  }

  /**
   * Finds Java home directories installed by <a href="https://github.com/sdkman">SDKMAN</a>.
   */
  private @NotNull Set<@NotNull String> findJavaInstalledBySdkMan() {
    try {
      Path candidatesDir = findSdkManCandidatesDir();
      if (candidatesDir == null) return Collections.emptySet();
      Path javasDir = candidatesDir.resolve("java");
      if (!Files.isDirectory(javasDir)) return Collections.emptySet();
      //noinspection UnnecessaryLocalVariable
      var homes = listJavaHomeDirsInstalledBySdkMan(javasDir);
      return homes;
    }
    catch (Exception e) {
      log.warn("Unexpected exception while looking for Sdkman directory: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
      return Collections.emptySet();
    }
  }

  private @NotNull Set<String> findJavaInstalledByGradle() {
    Path jdks = getPathInUserHome(".gradle/jdks");
    return jdks != null && Files.isDirectory(jdks) ? scanAll(jdks, true) : Collections.emptySet();
  }

  /**
   * Finds Java home directory installed by <a href="https://mise.jdx.dev/lang/java.html">mise</a>.
   */
  private @NotNull Set<String> findJavaInstalledByMise() {
    Path jdks = getPathInUserHome(".local/share/mise/installs/java/");
    if (jdks == null || !Files.isDirectory(jdks)) return Collections.emptySet();
    return scanAll(jdks, true).stream()
      .filter(path -> !Files.isSymbolicLink(Path.of(path)))
      .collect(Collectors.toSet());
  }

  private @Nullable Path findSdkManCandidatesDir() {
    // first, try the special environment variable
    String candidatesPath = mySystemInfo.getEnvironmentVariable("SDKMAN_CANDIDATES_DIR");
    if (candidatesPath != null) {
      Path candidatesDir = mySystemInfo.getPath(candidatesPath);
      if (Files.isDirectory(candidatesDir)) {
        return candidatesDir;
      }
    }

    // then, try to use its 'primary' variable
    String primaryPath = mySystemInfo.getEnvironmentVariable("SDKMAN_DIR");
    if (primaryPath != null) {
      Path candidatesDir = mySystemInfo.getPath(primaryPath, "candidates");
      if (Files.isDirectory(candidatesDir)) {
        return candidatesDir;
      }
    }

    // finally, try the usual location in UNIX
    if (!(this instanceof JavaHomeFinderWindows)) {
      Path candidates = getPathInUserHome(".sdkman/candidates");
      if (candidates != null && Files.isDirectory(candidates)) {
        return candidates;
      }
    }

    // no chances
    return null;
  }

  protected @Nullable Path getPathInUserHome(@NotNull String relativePath) {
    Path userHome = mySystemInfo.getUserHome();
    return userHome != null ? userHome.resolve(relativePath) : null;
  }

  private @NotNull Set<@NotNull String> listJavaHomeDirsInstalledBySdkMan(@NotNull Path javasDir) {
    var mac = this instanceof JavaHomeFinderMac;
    var result = new HashSet<@NotNull String>();

    try (Stream<Path> stream = Files.list(javasDir)) {
      List<Path> innerDirectories = stream.filter(d -> Files.isDirectory(d)).toList();
      for (Path innerDir : innerDirectories) {
        var home = innerDir;
        if (!JdkUtil.checkForJdk(home)) continue;
        var releaseFile = home.resolve("release");

        if (mac) {
          // Zulu JDK on macOS has a rogue layout, with which Gradle failed to operate (see the bugreport IDEA-253051),
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
      log.warn("I/O exception while listing Java home directories installed by Sdkman: " + ioe.getMessage(), ioe);
      return Collections.emptySet();
    }
    catch (Exception e) {
      log.warn("Unexpected exception while listing Java home directories installed by Sdkman: " +
               e.getClass().getSimpleName() +
               ": " +
               e.getMessage(), e);
      return Collections.emptySet();
    }

    return result;
  }


  /**
   * Finds Java home directories installed by <a href="https://github.com/halcyon/asdf-java">asdf-java</a>.
   */
  private @NotNull Set<String> findJavaInstalledByAsdfJava() {
    Path installsDir = findAsdfInstallsDir();
    if (installsDir == null) return Collections.emptySet();
    Path javasDir = installsDir.resolve("java");
    return safeIsDirectory(javasDir) ? scanAll(javasDir, true) : Collections.emptySet();
  }

  private @Nullable Path findAsdfInstallsDir() {
    // try to use environment variable for custom data directory
    // https://asdf-vm.com/#/core-configuration?id=environment-variables
    String dataDir = mySystemInfo.getEnvironmentVariable("ASDF_DATA_DIR");
    if (dataDir != null) {
      Path primaryDir = mySystemInfo.getPath(dataDir);
      if (safeIsDirectory(primaryDir)) {
        Path installsDir = primaryDir.resolve("installs");
        if (safeIsDirectory(installsDir)) return installsDir;
      }
    }

    // finally, try the usual location in Unix or macOS
    if (!(this instanceof JavaHomeFinderWindows) && !(this instanceof JavaHomeFinderWsl)) {
      Path installsDir = getPathInUserHome(".asdf/installs");
      if (installsDir != null && safeIsDirectory(installsDir)) return installsDir;
    }

    // no chances
    return null;
  }


  private boolean safeIsDirectory(@NotNull Path dir) {
    try {
      return Files.isDirectory(dir);
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
