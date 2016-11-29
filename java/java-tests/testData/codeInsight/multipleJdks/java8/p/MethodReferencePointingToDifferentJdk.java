package p;
import java.io.*;

class Subst {
  PersistentMap<Integer, String> messages;

  {
    register(() -> catchAndWarn(messages::close));
  }

  static void register(Runnable r) {}

  private static void catchAndWarn(ThrowableRunnable runnable) {
    try {
      runnable.run();
    }
    catch (IOException e) {}
  }

  interface ThrowableRunnable {
    void run() throws IOException;
  }
}
