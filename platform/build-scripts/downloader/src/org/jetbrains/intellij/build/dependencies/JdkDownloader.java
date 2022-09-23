// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Provides a reasonable stable version of JDK for current platform
 * <p>
 * JDK is used for compiling and running build scripts, compiling intellij project
 * It's currently fixed here to be the same on all build agents and also in Docker images
 */
public final class JdkDownloader {
  public static Path getJdkHome(BuildDependenciesCommunityRoot communityRoot, Consumer<String> infoLog) {
    OS os = OS.getCurrent();
    Arch arch = Arch.getCurrent();
    return getJdkHome(communityRoot, os, arch, infoLog);
  }

  public static Path getJdkHome(BuildDependenciesCommunityRoot communityRoot) {
    return getJdkHome(communityRoot, Logger.getLogger(JdkDownloader.class.getName())::info);
  }

  static Path getJdkHome(BuildDependenciesCommunityRoot communityRoot, OS os, Arch arch, Consumer<String> infoLog) {
    URI jdkUrl = getUrl(communityRoot, os, arch);

    Path jdkArchive = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, jdkUrl);
    Path jdkExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, jdkArchive, BuildDependenciesExtractOptions.STRIP_ROOT);
    infoLog.accept("jps-bootstrap JDK is at " + jdkExtracted);

    Path jdkHome;
    if (os == OS.MACOSX) {
      jdkHome = jdkExtracted.resolve("Contents").resolve("Home");
    }
    else {
      jdkHome = jdkExtracted;
    }

    Path executable = getJavaExecutable(jdkHome);
    infoLog.accept("JDK home is at " + jdkHome + ", executable at " + executable);

    return jdkHome;
  }

  public static Path getJavaExecutable(Path jdkHome) {
    for (String candidateRelative : Arrays.asList("bin/java", "bin/java.exe")) {
      Path candidate = jdkHome.resolve(candidateRelative);
      if (Files.exists(candidate)) {
        return candidate;
      }
    }

    throw new IllegalStateException("No java executables were found under " + jdkHome);
  }

  // TODO: convert to enhanced switch when build level is fixed
  @SuppressWarnings("EnhancedSwitchMigration")
  private static URI getUrl(BuildDependenciesCommunityRoot communityRoot, OS os, Arch arch) {
    String archString;
    String osString;
    String version;
    String build;
    String ext = ".tar.gz";

    switch (os) {
      case WINDOWS:
        osString = "windows";
        break;
      case MACOSX:
        osString = "osx";
        break;
      case LINUX:
        osString = "linux";
        break;
      default:
        throw new IllegalStateException("Unsupported OS: " + os);
    }

    switch (arch) {
      case X86_64:
        archString = "x64";
        break;
      case ARM64:
        archString = "aarch64";
        break;
      default:
        throw new IllegalStateException("Unsupported arch: " + arch);
    }

    var dependenciesProperties = BuildDependenciesDownloader.getDependenciesProperties(communityRoot);
    var jdkBuild = dependenciesProperties.property("jdkBuild");
    var jdkBuildSplit = jdkBuild.split("b");
    if (jdkBuildSplit.length != 2) {
      throw new IllegalStateException("Malformed jdkBuild property: " + jdkBuild);
    }
    version = jdkBuildSplit[0];
    build = "b" + jdkBuildSplit[1];

    return URI.create("https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-" +
                      version + "-" + osString + "-" +
                      archString + "-" + build + ext);
  }

  enum OS {
    WINDOWS, MACOSX, LINUX;

    public static OS getCurrent() {
      String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

      if (osName.startsWith("mac")) {
        return MACOSX;
      }
      else if (osName.startsWith("linux")) {
        return LINUX;
      }
      else if (osName.startsWith("windows")) {
        return WINDOWS;
      }
      else {
        throw new IllegalStateException("Only Mac/Linux/Windows are supported now, current os: " + osName);
      }
    }
  }

  enum Arch {
    X86_64, ARM64;

    public static Arch getCurrent() {
      String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
      if ("x86_64".equals(arch) || "amd64".equals(arch)) return X86_64;
      if ("aarch64".equals(arch) || "arm64".equals(arch)) return ARM64;
      throw new IllegalStateException("Only X86_64 and ARM64 are supported, current arch: " + arch);
    }
  }
}
