// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.ExecutorService;

public abstract class JdkVersionDetector {

  public static JdkVersionDetector getInstance() {
    return JpsServiceManager.getInstance().getService(JdkVersionDetector.class);
  }

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath);

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ExecutorService actionRunner);


  public static final class JdkVersionInfo {

    public final JavaVersion version;
    public final Bitness bitness;
    public final @Nullable String vendorPrefix;
    public final @Nullable String displayName;

    public JdkVersionInfo(@NotNull JavaVersion version, @NotNull Bitness bitness) {
      this.version = version;
      this.bitness = bitness;
      this.vendorPrefix = null;
      this.displayName = null;
    }

    public JdkVersionInfo(@NotNull JavaVersion version, @NotNull Bitness bitness, @Nullable String vendorPrefix, @Nullable String displayName) {
      this.version = version;
      this.bitness = bitness;
      this.vendorPrefix = vendorPrefix;
      this.displayName = displayName;
    }

    @NotNull
    public String suggestedName() {
      String f = version.toFeatureString();
      return vendorPrefix != null ? vendorPrefix + '-' + f : f;
    }

    @Override
    public String toString() {
      return version + " " + bitness;
    }

    public String displayVersionString() {
      String s = "version " + version;
      String d = displayName;
      if (d != null) s = d + ' ' + s;
      return s;
    }
  }

  @NotNull
  public static String formatVersionString(@NotNull JavaVersion version) {
    return "java version \"" + version + '"';
  }

  public static boolean isVersionString(@NotNull String string) {
    return string.length() >= 16 && string.startsWith("java version \"") && StringUtil.endsWithChar(string, '"');
  }
}