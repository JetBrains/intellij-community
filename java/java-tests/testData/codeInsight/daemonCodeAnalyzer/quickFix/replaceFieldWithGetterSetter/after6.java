// "Replace with getter" "true"
import java.io.File;

class Foo {
  void foo(File f) {
    String ss = f.getPath();
  }
}