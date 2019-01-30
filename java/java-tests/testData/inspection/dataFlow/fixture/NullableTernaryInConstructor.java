import java.io.*;

class Nullable {
  private final InputStream is;
  private final InputStream is2;

  public Nullable(boolean createNew, InputStream other) {
    is = createNew ? other : null;
    is2 = createNew ? null : null;
  }

  public void close() throws IOException {
    is.close(); // not supported yet
    is2.<warning descr="Method invocation 'close' may produce 'NullPointerException'">close</warning>();
  }
}