import org.jetbrains.annotations.Nullable;

import java.io.File;

class Doo {

  private static void doSomething(@Nullable File file) {
    while (file != null) {
      calc(file);
      file = file.getParentFile();
    }
  }
  private static void calc(File file) {
  }
}