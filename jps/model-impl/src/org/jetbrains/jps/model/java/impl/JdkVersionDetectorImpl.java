// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JdkVersionDetectorImpl extends JdkVersionDetector {
  private static final Logger LOG = Logger.getInstance(JdkVersionDetectorImpl.class);

  @Override
  public @Nullable JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath) {
    return detectJdkVersionInfo(homePath, SharedThreadPool.getInstance());
  }

  @Override
  public @Nullable JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ExecutorService runner) {
    // Java 1.7+
    JdkVersionInfo version = detectFromRelease(homePath);
    if (version != null) return version;

    // Java 1.2 - 1.8
    version = detectFromJar(homePath);
    if (version != null) return version;

    // last resort
    return detectFromOutput(homePath, runner);
  }

  private static @Nullable JdkVersionInfo detectFromRelease(String homePath) {
    Path releaseFile = Paths.get(homePath, "release");
    if (Files.isRegularFile(releaseFile)) {
      Properties p = new Properties();
      try (InputStream stream = Files.newInputStream(releaseFile)) {
        p.load(stream);
        String versionString = p.getProperty("JAVA_FULL_VERSION", p.getProperty("JAVA_VERSION"));
        if (versionString != null) {
          JavaVersion version = JavaVersion.parse(versionString);

          Variant variant = detectVariant(p);
          if (variant == null && version.feature < 9) {
            // pre-modular release files rarely contain enough information
            JdkVersionInfo fromJar = detectFromJar(homePath);
            if (fromJar != null) variant = fromJar.variant;
          }

          CpuArch arch = CpuArch.fromString(unquoteProperty(p, "OS_ARCH"));

          return new JdkVersionInfo(version, variant, arch);
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(releaseFile.toString(), e);
      }
    }

    return null;
  }

  private static @Nullable JdkVersionInfo detectFromJar(String homePath) {
    Path rtFile = Paths.get(homePath, "jre/lib/rt.jar");
    if (Files.isRegularFile(rtFile)) {
      try (JarFile rtJar = new JarFile(rtFile.toFile(), false)) {
        Manifest manifest = rtJar.getManifest();
        if (manifest != null) {
          String versionString = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          if (versionString != null) {
            JavaVersion version = JavaVersion.parse(versionString);
            boolean x64 = SystemInfo.isMac || Files.isDirectory(rtFile.resolveSibling("amd64"));
            String vendorString = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            Variant variant = vendorString != null ? detectVendor(vendorString) : null;
            return new JdkVersionInfo(version, variant, x64 ? CpuArch.X86_64 : CpuArch.UNKNOWN);
          }
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(rtFile.toString(), e);
      }
    }

    return null;
  }

  private static @Nullable JdkVersionInfo detectFromOutput(String homePath, ExecutorService runner) {
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
          return new JdkVersionInfo(version, null, CpuArch.UNKNOWN);
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(javaExe.toString(), e);
      }
    }

    return null;
  }

  private static @Nullable String unquoteProperty(Properties properties, String name) {
    String value = properties.getProperty(name);
    if (value != null && value.length() >= 2 && value.charAt(0) == '"') value = value.substring(1, value.length() - 1);
    return value;
  }

  private static @Nullable Variant detectVariant(Properties p) {
    String implementorVersion = unquoteProperty(p, "IMPLEMENTOR_VERSION");
    if (implementorVersion != null) {
      if (implementorVersion.startsWith("AdoptOpenJDK")) {
        String variant = unquoteProperty(p, "JVM_VARIANT");
        return "OpenJ9".equalsIgnoreCase(variant) ? Variant.AdoptOpenJdk_J9 : Variant.AdoptOpenJdk_HS;
      }
      if (implementorVersion.startsWith("Corretto")) return Variant.Corretto;
      if (implementorVersion.endsWith("-IBM")) return Variant.IBM;
      if (implementorVersion.startsWith("JBR-")) return Variant.JBR;
      if (implementorVersion.startsWith("SapMachine")) return Variant.SapMachine;
      if (implementorVersion.startsWith("Zulu")) return Variant.Zulu;
    }

    String implementor = unquoteProperty(p, "IMPLEMENTOR");
    if (implementor != null) {
      if (implementor.startsWith("AdoptOpenJDK")) {
        String variant = unquoteProperty(p, "JVM_VARIANT");
        return "OpenJ9".equalsIgnoreCase(variant) ? Variant.AdoptOpenJdk_J9 : Variant.AdoptOpenJdk_HS;
      }
      if (implementor.startsWith("GraalVM")) return Variant.GraalVM;
      return detectVendor(implementor);
    }

    if (p.getProperty("GRAALVM_VERSION") != null) return Variant.GraalVM;

    return null;
  }

  private static @Nullable Variant detectVendor(String implementor) {
    if (implementor.startsWith("Amazon")) return Variant.Corretto;
    if (implementor.startsWith("Azul")) return Variant.Zulu;
    if (implementor.startsWith("BellSoft")) return Variant.Liberica;
    if (implementor.startsWith("Eclipse")) return Variant.Temurin;
    if (implementor.startsWith("IBM")) return Variant.IBM;
    if (implementor.startsWith("International Business")) return Variant.Semeru;
    if (implementor.startsWith("JetBrains")) return Variant.JBR;
    if (implementor.startsWith("Oracle")) return Variant.Oracle;
    if (implementor.startsWith("SAP")) return Variant.SapMachine;
    return null;
  }

  private static class VersionOutputReader extends BaseOutputReader {
    private static final BaseOutputReader.Options OPTIONS = new BaseOutputReader.Options() {
      @Override public SleepingPolicy policy() { return SleepingPolicy.BLOCKING; }
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

    @Override
    protected @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return myRunner.submit(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myLines.add(text);
      LOG.trace("text: " + text);
    }
  }
}
