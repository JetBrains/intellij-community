// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.info;

/**
 * Provides a reasonable stable version of JDK 11 for current platform
 * <p>
 * JDK is used for compiling and running build scripts, compiling intellij project
 * It's currently fixed here to be the same on all build agents and also in Docker images
 */
public final class Jdk11Downloader {
  private static final String CORRETTO_VERSION = "11.0.14.9.1";

  // Corretto 11 is not available on macOS aarch64
  private static final URI ZULU_MACOS_AARCH64_URL = URI.create("https://cache-redirector.jetbrains.com/cdn.azul.com/zulu/bin/zulu11.50.19-ca-jdk11.0.12-macosx_aarch64.tar.gz");

  public static Path getJdkHome(BuildDependenciesCommunityRoot communityRoot) {
    OS os = OS.getCurrent();
    Arch arch = Arch.getCurrent();
    return getJdkHome(communityRoot, os, arch);
  }

  static Path getJdkHome(BuildDependenciesCommunityRoot communityRoot, OS os, Arch arch) {
    URI jdkUrl;
    if (os == OS.MACOSX && arch == Arch.ARM64) {
      // Corretto 11 is not available on macOS aarch64
      jdkUrl = ZULU_MACOS_AARCH64_URL;
    }
    else {
      jdkUrl = getCorrettoUrl(os, arch);
    }

    Path jdkArchive = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, jdkUrl);
    Path jdkExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, jdkArchive, BuildDependenciesExtractOptions.STRIP_ROOT);
    info("jps-bootstrap JDK is at " + jdkExtracted);

    if (jdkUrl == ZULU_MACOS_AARCH64_URL) {
      jdkExtracted = jdkExtracted.resolve("zulu-11.jdk");
    }

    Path jdkHome;
    if (os == OS.MACOSX) {
      jdkHome = jdkExtracted.resolve("Contents").resolve("Home");
    }
    else {
      jdkHome = jdkExtracted;
    }

    Path executable = getJavaExecutable(jdkHome);
    info("JDK home is at " + jdkHome + ", executable at " + executable);

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

  private static URI getCorrettoUrl(OS os, Arch arch) {
    String archString;
    String osString;
    String ext = os == OS.WINDOWS ? ".zip" : ".tar.gz";
    String suffix = os == OS.WINDOWS ? "-jdk" : "";

    switch (os) {
      case WINDOWS:
        osString = "windows";
        break;
      case MACOSX:
        osString = "macosx";
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

    return URI.create("https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/" +
      CORRETTO_VERSION + "/amazon-corretto-" + CORRETTO_VERSION + "-" + osString + "-" + archString + suffix + ext);
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
