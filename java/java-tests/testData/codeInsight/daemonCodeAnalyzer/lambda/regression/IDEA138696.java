import java.io.IOException;

class CommandTest {

  public URL someFunction(URI uri) {
    return unchecked(uri::toURL);
  }

  public interface UncheckedRun<T> {
    public T run() throws Throwable;
  }

  public static <T> T unchecked(UncheckedRun<T> run) {
    try {
      return run.run();
    } catch (Throwable throwable) {
      throw new AssertionError();
    }
  }
}

abstract class URI {
  abstract URL toURL() throws IOException;
}

class URL {}