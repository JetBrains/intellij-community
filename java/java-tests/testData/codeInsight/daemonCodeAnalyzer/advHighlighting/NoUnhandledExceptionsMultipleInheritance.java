import java.io.*;

interface ThrowsCloneNotSupportedException {
  void f() throws CloneNotSupportedException;
}

interface ThrowsIOException {
  void f() throws IOException;
}

abstract class ThrowsNothing implements ThrowsCloneNotSupportedException, ThrowsIOException {
  private void foo() {
    f();
  }
}

class Main {
  public static void main(String[] args) {
    ThrowsNothing throwsNothing = null;
    throwsNothing.f();
  }
}
