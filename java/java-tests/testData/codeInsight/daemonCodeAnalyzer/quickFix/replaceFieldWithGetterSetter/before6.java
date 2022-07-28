// "Replace with getter" "true-preview"
import java.io.File;

class Foo {
  void foo(File f) {
    String ss = f.<caret>path;
  }
}