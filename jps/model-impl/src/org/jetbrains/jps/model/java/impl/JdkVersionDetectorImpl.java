// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.jetbrains.jps.model.java.impl.JdkVendorDetector.detectJdkVendorByReleaseFile;

public class JdkVersionDetectorImpl extends JdkVersionDetector {
  private static final Logger LOG = Logger.getInstance(JdkVersionDetectorImpl.class);

  @Nullable
  @Override
  public JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath) {
    return detectJdkVersionInfo(homePath, SharedThreadPool.getInstance());
  }

  @Nullable
  @Override
  public JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ExecutorService runner) {
    // Java 1.7+
    Path releaseFile = Paths.get(homePath, "release");
    if (Files.isRegularFile(releaseFile)) {
      Properties p = new Properties();
      try (InputStream stream = Files.newInputStream(releaseFile)) {
        p.load(stream);
        String versionString = p.getProperty("JAVA_FULL_VERSION", p.getProperty("JAVA_VERSION"));
        if (versionString != null) {
          JavaVersion version = JavaVersion.parse(versionString);
          String arch = StringUtil.unquoteString(p.getProperty("OS_ARCH", ""));
          boolean x64 = "x86_64".equals(arch) || "amd64".equals(arch);
          Bitness bitness = x64 ? Bitness.x64 : Bitness.x32;
          JdkVendorDetector.Vendor vendor = detectJdkVendorByReleaseFile(p);
          return vendor != null
                 ? new JdkVersionInfo(version, bitness, vendor.getPrefix(), vendor.displayName)
                 : new JdkVersionInfo(version, bitness);
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(releaseFile.toString(), e);
      }
    }

    // Java 1.2 - 1.8
    Path rtFile = Paths.get(homePath, "jre/lib/rt.jar");
    if (Files.isRegularFile(rtFile)) {
      try (JarFile rtJar = new JarFile(rtFile.toFile(), false)) {
        Manifest manifest = rtJar.getManifest();
        if (manifest != null) {
          String versionString = manifest.getMainAttributes().getValue("Implementation-Version");
          if (versionString != null) {
            JavaVersion version = JavaVersion.parse(versionString);
            boolean x64 = SystemInfo.isMac || Files.isDirectory(rtFile.resolveSibling( "amd64"));
            return new JdkVersionInfo(version, x64 ? Bitness.x64 : Bitness.x32);
          }
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(rtFile.toString(), e);
      }
    }

    // last resort
    Path javaExe = Paths.get(homePath, "bin/" + (SystemInfo.isWindows ? "java.exe" : "java"));
    if (Files.isExecutable(javaExe)) {
      try {
        Process process = new ProcessBuilder(javaExe.toString(), "-version").redirectErrorStream(true).start();
        VersionOutputReader reader = new VersionOutputReader(process.getInputStream(), runner);
        try {
          reader.waitFor();
        }
        catch (InterruptedException e) {
          LOG.info(e);
          process.destroy();
        }

        List<String> lines = reader.myLines;
        while (!lines.isEmpty() && lines.get(0).startsWith("Picked up ")) {
          lines.remove(0);
        }
        if (!lines.isEmpty()) {
          JavaVersion base = JavaVersion.parse(lines.get(0));
          JavaVersion rt = JavaVersion.tryParse(lines.size() > 2 ? lines.get(1) : null);
          JavaVersion version = rt != null && rt.feature == base.feature && rt.minor == base.minor ? rt : base;
          boolean x64 = lines.stream().anyMatch(s -> s.contains("64-Bit") || s.contains("x86_64") || s.contains("amd64"));
          return new JdkVersionInfo(version, x64 ? Bitness.x64 : Bitness.x32);
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(javaExe.toString(), e);
      }
    }

    return null;
  }

  private static class VersionOutputReader extends BaseOutputReader {
    private static final BaseOutputReader.Options OPTIONS = new BaseOutputReader.Options() {
      @Override public SleepingPolicy policy() { return SleepingPolicy.BLOCKING; }
      @Override public boolean splitToLines() { return true; }
      @Override public boolean sendIncompleteLines() { return false; }
      @Override public boolean withSeparators() { return false; }
    };

    private final ExecutorService myRunner;
    private final List<String> myLines;

    VersionOutputReader(@NotNull InputStream stream, @NotNull ExecutorService runner) {
      super(stream, CharsetToolkit.getDefaultSystemCharset(), OPTIONS);
      myRunner = runner;
      myLines = new CopyOnWriteArrayList<>();
      start("java -version");
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return myRunner.submit(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myLines.add(text);
      LOG.trace("text: " + text);
    }
  }
}