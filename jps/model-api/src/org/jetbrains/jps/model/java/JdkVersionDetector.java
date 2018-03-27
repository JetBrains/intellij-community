/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Bitness;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.Future;

/**
 * @author nik
 */
public abstract class JdkVersionDetector {
  public static JdkVersionDetector getInstance() {
    return JpsServiceManager.getInstance().getService(JdkVersionDetector.class);
  }

  /** @deprecated use {@link #detectJdkVersionInfo(String)} (to be removed in IDEA 2019) */
  @Nullable
  @SuppressWarnings("deprecation")
  public String detectJdkVersion(@NotNull String homePath) {
    JdkVersionInfo info = detectJdkVersionInfo(homePath);
    return info != null ? info.getVersion() : null;
  }

  /** @deprecated use {@link #detectJdkVersionInfo(String, ActionRunner)} (to be removed in IDEA 2019) */
  @Nullable
  @SuppressWarnings("deprecation")
  public String detectJdkVersion(@NotNull String homePath, @NotNull ActionRunner runner) {
    JdkVersionInfo info = detectJdkVersionInfo(homePath, runner);
    return info != null ? info.getVersion() : null;
  }

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath);

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ActionRunner actionRunner);

  //todo[nik] replace with a service with different implementations for IDE process and for JPS process (need to exclude jps-builders module from IDEA classpath)
  public interface ActionRunner {
    Future<?> run(Runnable runnable);
  }

  public static final class JdkVersionInfo {
    public final JavaVersion version;
    public final Bitness bitness;

    public JdkVersionInfo(@NotNull JavaVersion version, @NotNull Bitness bitness) {
      this.version = version;
      this.bitness = bitness;
    }

    @Override
    public String toString() {
      return version + " " + bitness;
    }

    /** @deprecated use {@link #version} (to be removed in IDEA 2019) */
    public String getVersion() {
      return formatVersionString(version);
    }

    /** @deprecated use {@link #bitness} (to be removed in IDEA 2019) */
    public Bitness getBitness() {
      return bitness;
    }
  }

  public static String formatVersionString(@NotNull JavaVersion version) {
    return "java version \"" + version + '"';
  }
}