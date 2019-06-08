// "Add exception to existing catch clause" "true"
import java.io.IOException;

class X extends RuntimeException {}
class Y extends RuntimeException {}

class Test {
  public static void main(String[] args) {
    try {
      try {
        throw new <caret>IOException();
      }
      catch (X x) {
      }
    }
    catch (Y y) {
    }
  }
}