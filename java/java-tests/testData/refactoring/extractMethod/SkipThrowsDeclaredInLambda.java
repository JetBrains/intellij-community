import java.io.IOException;
class Issue {

  public static void main(String[] args) {
    <selection>swallow(() -> {
      throw new IOException();
    });</selection>
  }

  private static void swallow(ThrowsUnchecked r) {
    try {
      r.doAction();
    } catch (IOException ignored) {
    }
  }
}

interface ThrowsUnchecked {
  void doAction() throws IOException;
}