// IDEA-304296
class Example {
  public static void main(String[] args) {
    boolean done = false;
    try (final ThrowsOnClose resource = new ThrowsOnClose()) {
      resource.foo();
      done = true;
    } catch (RuntimeException e) {
      if (!done) {
        throw e;
      }
    }
  }

  private static class ThrowsOnClose implements AutoCloseable {
    public void foo() {
    }

    @Override
    public void close() {
      throw new RuntimeException();
    }
  }
}