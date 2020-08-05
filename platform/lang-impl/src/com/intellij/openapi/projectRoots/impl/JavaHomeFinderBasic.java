// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaHomeFinderBasic {
  private final Logger LOG = Logger.getInstance(getClass());
  private final List<Supplier<Set<String>>> myFinders = new ArrayList<>();

  JavaHomeFinderBasic(boolean forceEmbeddedJava, String... paths) {
    myFinders.add(this::checkDefaultLocations);
    myFinders.add(this::findInPATH);
    myFinders.add(() -> findInSpecifiedPaths(paths));
    myFinders.add(this::findJavaInstalledBySdkMan);

    if (forceEmbeddedJava || Registry.is("java.detector.include.embedded", false)) {
      myFinders.add(() -> scanAll(getJavaHome(), false));
    }
  }

  private @NotNull Set<String> findInSpecifiedPaths(String[] paths) {
    return scanAll(Stream.of(paths).map(it -> Paths.get(it)).collect(Collectors.toList()), true);
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
        LOG.warn("Failed to find Java Home. " + e.getMessage(), e);
      }
    }

    return result;
  }

  private @NotNull Set<String> findInPATH() {
    try {
      String pathVarString = EnvironmentUtil.getValue("PATH");
      if (pathVarString == null || pathVarString.isEmpty()) {
        return Collections.emptySet();
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
      LOG.warn("Failed to scan PATH for JDKs. " + e.getMessage(), e);
      return Collections.emptySet();
    }
  }

  @NotNull
  private Set<String> checkDefaultLocations() {
    if (ApplicationManager.getApplication() == null) {
      return Collections.emptySet();
    }

    Set<Path> paths = new HashSet<>();
    paths.add(JdkInstaller.getInstance().defaultInstallDir());

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
      return Collections.emptySet();
    }
    return scanAll(Collections.singleton(file), includeNestDirs);
  }

  protected @NotNull Set<String> scanAll(@NotNull Collection<Path> files, boolean includeNestDirs) {
    Set<String> result = new HashSet<>();
    for (Path root : new HashSet<>(files)) {
      scanFolder(root.toFile(), includeNestDirs, result);
    }
    return result;
  }

  private void scanFolder(@NotNull File folder, boolean includeNestDirs, @NotNull Collection<? super String> result) {
    if (JdkUtil.checkForJdk(folder)) {
      result.add(folder.getAbsolutePath());
      return;
    }

    if (!includeNestDirs) return;
    File[] files = folder.listFiles();
    if (files == null) return;

    for (File candidate : files) {
      for (File adjusted : listPossibleJdkHomesFromInstallRoot(candidate)) {
        scanFolder(adjusted, false, result);
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

  protected static @Nullable Path getJavaHome() {
    String property = SystemProperties.getJavaHome();
    if (property == null || property.isEmpty()) {
      return null;
    }

    // actually java.home points to to jre home
    Path javaHome = Paths.get(property).getParent();
    return javaHome == null || !Files.isDirectory(javaHome) ? null : javaHome;
  }


  /**
   * Finds Java home directories installed by SDKMAN: https://github.com/sdkman
   */
  private @NotNull Set<String> findJavaInstalledBySdkMan() {
    File candidatesDir = findSdkManCandidatesDir();
    if (candidatesDir == null) return Collections.emptySet();
    File javasDir = new File(candidatesDir, "java");
    return javasDir.isDirectory() ? scanAll(javasDir.toPath(), true) : Collections.emptySet();
  }

  @Nullable
  private static File findSdkManCandidatesDir() {
    // first, try the special environment variable
    String candidatesPath = EnvironmentUtil.getValue("SDKMAN_CANDIDATES_DIR");
    if (candidatesPath != null) {
      File candidatesDir = new File(candidatesPath);
      if (candidatesDir.isDirectory()) return candidatesDir;
    }

    // then, try to use its 'primary' variable
    String primaryPath = EnvironmentUtil.getValue("SDKMAN_DIR");
    if (primaryPath != null) {
      File primaryDir = new File(primaryPath);
      if (primaryDir.isDirectory()) {
        File candidatesDir = new File(primaryDir, "candidates");
        if (candidatesDir.isDirectory()) return candidatesDir;
      }
    }

    // finally, try the usual location in Unix or MacOS
    if (!SystemInfo.isWindows) {
      String homePath = System.getProperty("user.home");
      if (homePath != null) {
        File homeDir = new File(homePath);
        File primaryDir = new File(homeDir, ".sdkman");
        File candidatesDir = new File(primaryDir, "candidates");
        if (candidatesDir.isDirectory()) return candidatesDir;
      }
    }

    // no chances
    return null;
  }
}
