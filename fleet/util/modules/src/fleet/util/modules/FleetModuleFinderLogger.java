package fleet.util.modules;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface FleetModuleFinderLogger {
  void warn(@NotNull Supplier<String> message);
  void error(@Nullable Throwable t, @NotNull Supplier<String> message);
}
