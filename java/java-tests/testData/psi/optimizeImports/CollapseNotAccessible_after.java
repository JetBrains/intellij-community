package abc;

import imports.Holder;

import java.nio.file.Files;
import java.nio.file.Path;

import static imports.Holder.*;

public class Other {

  public static void main(String[] args) {
    Holder h = new Holder();
  }

  void m() {
    Files.exists(Path.of("."));
    x(A);
    x(B);
    x(C);
  }

  void x(int i) { }
}