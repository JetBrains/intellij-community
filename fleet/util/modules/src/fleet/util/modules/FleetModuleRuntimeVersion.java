package fleet.util.modules;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FleetModuleRuntimeVersion implements Comparable<FleetModuleRuntimeVersion> {
  public static @Nullable FleetModuleRuntimeVersion parseVersion(String version) {
    try {
      return new FleetModuleRuntimeVersion(Runtime.Version.parse(version), version);
    }
    catch (Throwable ignore) {
      return null;
    }
  }

  private final @NotNull Runtime.Version runtimeVersion;

  private final @NotNull String rawVersion;

  public FleetModuleRuntimeVersion(@NotNull Runtime.Version runtimeVersion,
                                   @NotNull String rawVersion) {
    this.runtimeVersion = runtimeVersion;
    this.rawVersion = rawVersion;
  }

  public @NotNull Runtime.Version getRuntimeVersion() {
    return runtimeVersion;
  }

  public @NotNull String getRawVersion() {
    return rawVersion;
  }

  @Override
  public int compareTo(@NotNull FleetModuleRuntimeVersion o) {
    int runtimeVersionCompare = runtimeVersion.compareTo(o.runtimeVersion);
    return runtimeVersionCompare != 0 ? runtimeVersionCompare : rawVersion.compareTo(o.rawVersion);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FleetModuleRuntimeVersion version = (FleetModuleRuntimeVersion)o;
    return Objects.equals(runtimeVersion, version.runtimeVersion) && Objects.equals(rawVersion, version.rawVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(runtimeVersion, rawVersion);
  }
}
