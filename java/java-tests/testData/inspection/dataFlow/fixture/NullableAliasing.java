import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class NullableAliasing {
  @Nullable
  private Object c0 = File.separator;
  @NotNull
  private Object c1 = File.separator;
}
