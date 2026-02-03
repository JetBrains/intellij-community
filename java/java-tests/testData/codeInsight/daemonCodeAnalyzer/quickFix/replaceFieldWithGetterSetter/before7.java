// "Replace with setter" "false"
import java.io.File;

class Foo {
  void foo(File f) {
    f.<caret>path += "//";
  }
}