// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class JdkVersionDetector {
  public static JdkVersionDetector getInstance() {
    return JpsServiceManager.getInstance().getService(JdkVersionDetector.class);
  }

  @ApiStatus.Internal
  protected JdkVersionDetector() {
  }

  public abstract @Nullable JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath);

  public abstract @Nullable JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ExecutorService actionRunner);

  @SuppressWarnings("SpellCheckingInspection")
  public enum Variant {
    AdoptOpenJdk_HS("adopt", "AdoptOpenJDK (HotSpot)"),
    AdoptOpenJdk_J9("adopt-j9", "AdoptOpenJDK (OpenJ9)"),
    Temurin("temurin", "Eclipse Temurin"),
    Semeru("semeru", "IBM Semeru"),
    Corretto("corretto", "Amazon Corretto"),
    GraalVMCE("graalvm-ce", "GraalVM CE"),
    GraalVM("graalvm", "GraalVM"),
    IBM("ibm", "IBM JDK"),
    JBR("jbr", "JetBrains Runtime"),
    Liberica("liberica", "BellSoft Liberica"),
    Oracle(null, "Oracle OpenJDK"),
    SapMachine("sap", "SAP SapMachine"),
    Zulu("zulu", "Azul Zulu"),
    Homebrew("homebrew", "Homebrew OpenJDK"),
    Unknown(null, "Unknown");

    public final @Nullable String prefix;
    public final @NotNull String displayName;

    Variant(@Nullable String prefix, @NotNull String displayName) {
      this.prefix = prefix;
      this.displayName = displayName;
    }
  }

  @ApiStatus.Internal
  public static final List<String> VENDORS = ContainerUtil.map(Variant.values(), v -> v.displayName);

  public static final class JdkVersionInfo {
    public final JavaVersion version;
    public final Variant variant;
    public final CpuArch arch;
    public final String graalVersion;

    public JdkVersionInfo(@NotNull JavaVersion version, @Nullable Variant variant, @NotNull CpuArch arch) {
      this(version, variant, arch, null);
    }

    public JdkVersionInfo(@NotNull JavaVersion version, @Nullable Variant variant, @NotNull CpuArch arch, @Nullable String graalVersion) {
      this.version = version;
      this.variant = variant != null ? variant : Variant.Unknown;
      this.arch = arch;
      this.graalVersion = graalVersion;
    }

    public @NotNull String suggestedName() {
      String f = version.toFeatureString();
      return variant.prefix != null ? variant.prefix + '-' + f : f;
    }

    public @NotNull @NlsSafe String displayVersionString() {
      var s = "";
      if (variant != Variant.Unknown) s += variant.displayName + ' ';
      s += version;
      if (graalVersion != null) s += " - VM " + graalVersion;
      if (arch == CpuArch.ARM64) s += " - aarch64";
      return s;
    }

    @Override
    public String toString() {
      return version + " " + arch;
    }
  }

  public static @NotNull String formatVersionString(@NotNull JavaVersion version) {
    return "java version \"" + version + '"';
  }

  public static boolean isVersionString(@NotNull String string) {
    return string.length() >= 16 && string.startsWith("java version \"") && StringUtil.endsWithChar(string, '"');
  }
}
