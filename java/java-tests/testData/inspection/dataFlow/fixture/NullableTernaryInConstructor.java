import java.io.*;
import org.jetbrains.annotations.Nullable;

class NullableTernaryInConstructor {
  private final InputStream is;
  private final InputStream is2;
  private final InputStream is3;

  public NullableTernaryInConstructor(boolean createNew, InputStream other, @Nullable InputStream third) {
    is = createNew ? other : null;
    is2 = createNew ? null : null;
    is3 = createNew ? third : null;
  }

  public void close() throws IOException {
    is.close(); // not supported yet
    is2.<warning descr="Method invocation 'close' may produce 'NullPointerException'">close</warning>();
    is3.<warning descr="Method invocation 'close' may produce 'NullPointerException'">close</warning>();
  }
}