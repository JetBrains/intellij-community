
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

class Test {

  {
    Callable<String> r0 = () -> {
      log();
      return "";
    };

    Callable<String> r = () -> {
      try {
        return "";
      }
      catch (Throwable ex) {
        log();
        return "";
      }
    };

    Callable<String> r1 = () -> {
      log();
      try {
        return "";
      }
      catch (Throwable ex) {
        return "";
      }
    };

    Callable<String> r2 = () -> {
      try (InputStream stream = new FileInputStream("")) {
        return  null;
      }
    };
  }


  private static void log() throws Exception {}
}