// "Add exception to existing catch clause" "false"
import java.io.File;

class Test {
  void test() {
    try { }
    catch (Error ex) {
      try { }
      catch (RuntimeException ex2) {
        Class.forName<caret>("xyz");
      }
    }
  }
}