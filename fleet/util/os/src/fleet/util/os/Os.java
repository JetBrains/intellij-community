// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.os;

import java.util.Locale;

/**
 * @deprecated Use {@link fleet.util.Os} instead.
 */
@Deprecated
public final class Os {
  // all these fields are for compatibility, we may replace them with something more manageable
  public static final Os INSTANCE = new Os();

  private Os() {
  }

  public enum Type {
    Windows, Linux, MacOS, Unknown
  }

  public String getName() {
    return System.getProperty("os.name");
  }

  public String getVersion() {
    return System.getProperty("os.version");
  }

  public String getArch() {
    return System.getProperty("os.arch");
  }

  public Type getType() {
    String normalizedName = getName().toLowerCase(Locale.ROOT);
    if (normalizedName.startsWith("mac")) {
      return Type.MacOS;
    }
    if (normalizedName.startsWith("win")) {
      return Type.Windows;
    }
    if (normalizedName.contains("nix") || normalizedName.contains("nux")) {
      return Type.Linux;
    }
    return Type.Unknown;
  }

  public boolean isAarch64() {
    String arch = getArch();
    return "aarch64".equals(arch) || "arm64".equals(arch);
  }

  public boolean isMac() {
    return getType() == Type.MacOS;
  }

  public boolean isWindows() {
    return getType() == Type.Windows;
  }

  public boolean isLinux() {
    return getType() == Type.Linux;
  }

  public boolean isUnix() {
    Type type = getType();
    return type == Type.Linux || type == Type.MacOS;
  }
}