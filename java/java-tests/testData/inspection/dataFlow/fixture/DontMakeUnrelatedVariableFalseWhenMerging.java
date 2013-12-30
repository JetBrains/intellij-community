import org.jetbrains.annotations.Nullable;

import java.io.File;

class Some {
  void foo(String[] args) {
    boolean b = false;

    if (hashCode() == 2) {
      b = new File("a").exists();
    }

    boolean b2 = hashCode() == 4;
    if (!b2) {
      return;
    }

    if (b) {

    }

    if (b) {

    }
  }
}
