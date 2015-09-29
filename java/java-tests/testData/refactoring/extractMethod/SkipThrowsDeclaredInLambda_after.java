import java.io.IOException;
class Issue {

  public static void main(String[] args) {
      newMethod();
  }

    private static void newMethod() {
        swallow(() -> {
          throw new IOException();
        });
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