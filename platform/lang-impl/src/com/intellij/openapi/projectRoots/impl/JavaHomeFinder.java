// Copyright 2000-2025 JetBrains s.r.o. aversionributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.eel.EelApi;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.platform.eel.path.EelPath;
import com.intellij.platform.eel.provider.EelNioBridgeServiceKt;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.projectRoots.impl.JavaHomeFinderEel.javaHomeFinderEel;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.EelProviderUtil.upgradeBlocking;

@ApiStatus.Internal
public abstract class JavaHomeFinder {
  public static class SystemInfoProvider {
    public @Nullable String getEnvironmentVariable(@NotNull String name) {
      return EnvironmentUtil.getValue(name);
    }

    public @NotNull Path getPath(String path, String... more) {
      return Path.of(path, more);
    }

    public @Nullable Path getUserHome() {
      return Path.of(SystemProperties.getUserHome());
    }

    public @NotNull Collection<@NotNull Path> getFsRoots() {
      Iterable<Path> rootDirectories = FileSystems.getDefault().getRootDirectories();
      return rootDirectories != null ? ContainerUtil.newArrayList(rootDirectories) : Collections.emptyList();
    }

    public String getPathSeparator() {
      return File.pathSeparator;
    }

    public boolean isFileSystemCaseSensitive() {
      return SystemInfoRt.isFileSystemCaseSensitive;
    }
  }

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible directories to start file chooser.
   * @return suggested sdk home paths (sorted)
   *
   * @deprecated Please use {@link JavaHomeFinder#suggestHomePaths(Project)}. The project can be located on a remote machine,
   * and the SDK should be local to the project, not to the IDE.
   */
  @Deprecated
  public static @NotNull List<String> suggestHomePaths() {
    return suggestHomePaths(false);
  }

  /**
   * Do the same as {@link #suggestHomePaths()} but always considers the embedded JRE,
   * for using in tests that are performed when the registry is not properly initialized
   * or that need the embedded JetBrains Runtime.
   */
  public static @NotNull List<String> suggestHomePaths(boolean forceEmbeddedJava) {
    return suggestHomePaths(LocalEelDescriptor.INSTANCE, forceEmbeddedJava);
  }

  /**
   * Tries to find Java SDKs on the machine where {@code project} is located.
   * If no JDK found, returns possible directories to start file chooser.
   *
   * @return suggested sdk home paths (sorted)
   */
  public static @NotNull List<@NotNull String> suggestHomePaths(@Nullable Project project) {
    return suggestHomePaths(project == null ? LocalEelDescriptor.INSTANCE : getEelDescriptor(project), false);
  }

  @ApiStatus.Internal
  public static @NotNull List<String> suggestHomePaths(@NotNull EelDescriptor eelDescriptor, boolean forceEmbeddedJava) {
    return ContainerUtil.map(findJdks(eelDescriptor, forceEmbeddedJava), JdkEntry::path);
  }

  @ApiStatus.Internal
  public record JdkEntry(@NotNull String path, @Nullable JdkVersionDetector.JdkVersionInfo versionInfo) implements Comparable<JdkEntry> {
    /// An entry should appear before another one if it has a **more recent** version or a shorter path.
    @Override
    public int compareTo(@NotNull JavaHomeFinder.JdkEntry o) {
      final var v1 = versionInfo != null ? versionInfo.version : null;
      final var v2 = o.versionInfo != null ? o.versionInfo.version : null;
      final int v = Comparing.compare(v2, v1);
      if (v != 0) return v;
      return Comparing.compare(path, o.path);
    }

    public @Nullable SdkType.SdkEntry toSdkEntry() {
      if (versionInfo == null) return null;
      return new SdkType.SdkEntry(path, versionInfo.displayVersionString());
    }
  }

  /**
   * Returns a list of {@link JdkEntry} containing information about the JDKs detected on the computer.
   */
  @ApiStatus.Internal
  public static @NotNull List<JdkEntry> findJdks(@NotNull EelDescriptor eelDescriptor, boolean forceEmbeddedJava) {
    JavaHomeFinderBasic javaFinder = getFinder(eelDescriptor, forceEmbeddedJava);
    if (javaFinder == null) return Collections.emptyList();

    return findJdks(javaFinder);
  }

  private static @NotNull ArrayList<JdkEntry> findJdks(JavaHomeFinderBasic javaFinder) {
    ArrayList<JdkEntry> paths = new ArrayList<>(javaFinder.findExistingJdkEntries());
    ContainerUtil.sort(paths);
    return paths;
  }

  private static boolean isDetectorEnabled(boolean forceEmbeddedJava) {
    return forceEmbeddedJava || Registry.is("java.detector.enabled", true);
  }

  private static JavaHomeFinderBasic getFinder(@NotNull EelDescriptor descriptor, boolean forceEmbeddedJava) {
    if (!isDetectorEnabled(forceEmbeddedJava)) return null;

    return getFinder(descriptor).checkEmbeddedJava(forceEmbeddedJava);
  }

  public static @NotNull JavaHomeFinderBasic getFinder(@Nullable Project project) {
    return getFinder(project == null ? LocalEelDescriptor.INSTANCE : getEelDescriptor(project));
  }

  private static @NotNull JavaHomeFinderBasic getFinder(@NotNull EelDescriptor descriptor) {
    if (Registry.is("java.home.finder.use.eel")) {
      return javaHomeFinderEel(descriptor);
    }

    SystemInfoProvider systemInfoProvider = new SystemInfoProvider();

    if (SystemInfo.isWindows) {
      return new JavaHomeFinderWindows(true, true, systemInfoProvider);
    }
    if (SystemInfo.isMac) {
      return new JavaHomeFinderMac(systemInfoProvider);
    }
    if (SystemInfo.isLinux) {
      return new JavaHomeFinderBasic(systemInfoProvider).checkSpecifiedPaths(DEFAULT_JAVA_LINUX_PATHS);
    }
    if (SystemInfo.isSolaris) {
      return new JavaHomeFinderBasic(systemInfoProvider).checkSpecifiedPaths("/usr/jdk");
    }

    return new JavaHomeFinderBasic(systemInfoProvider);
  }

  public static @Nullable String defaultJavaLocation(@Nullable Path path) {
    if (path != null && Registry.is("java.home.finder.use.eel")) {
      Path location = defaultJavaLocationUsingEel(path);
      if (location == null) {
        return null;
      }
      else {
        return location.toString();
      }
    }
    if (SystemInfo.isWindows) {
      return JavaHomeFinderWindows.defaultJavaLocation;
    }
    if (SystemInfo.isMac) {
      return JavaHomeFinderMac.defaultJavaLocation;
    }
    if (SystemInfo.isLinux) {
      return "/opt/java";
    }
    if (SystemInfo.isSolaris) {
      return "/usr/jdk";
    }
    return null;
  }

  private static @Nullable Path defaultJavaLocationUsingEel(Path path) {
    EelApi eel = upgradeBlocking(getEelDescriptor(path));
    EelPlatform platform = eel.getPlatform();
    String eelPath = null;
    if (platform instanceof EelPlatform.Windows) {
      eelPath = JavaHomeFinderWindows.defaultJavaLocation;
    }
    if (platform instanceof EelPlatform.Darwin) {
      eelPath = JavaHomeFinderMac.defaultJavaLocation;
    }
    if (platform instanceof EelPlatform.Linux) {
      String defaultLinuxPathRepresentation = "/opt/java";
      Path defaultLinuxPath = EelNioBridgeServiceKt.asNioPath(EelPath.parse(defaultLinuxPathRepresentation, eel.getDescriptor()));
      if (Files.exists(defaultLinuxPath)) {
        eelPath = defaultLinuxPathRepresentation;
      }
      else {
        eelPath = eel.getUserInfo().getHome().toString();
      }
    }
    if (eelPath != null) {
      EelPath absoluteLocation = EelPath.parse(eelPath, eel.getDescriptor());
      return EelNioBridgeServiceKt.asNioPathOrNull(absoluteLocation);
    }
    return null;
  }

  public static final String[] DEFAULT_JAVA_LINUX_PATHS = {"/usr/java", "/opt/java", "/usr/lib/jvm"};
}
