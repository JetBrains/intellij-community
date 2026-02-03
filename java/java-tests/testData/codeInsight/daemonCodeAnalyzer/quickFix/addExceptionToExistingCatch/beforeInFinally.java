// "Add exception to existing catch clause" "false"
import java.io.File;

class MyException extends Exception {}

class Test {
  public static void main(String[] args) {
    try {
    } catch (RuntimeException e) {
    } finally // comment
    {
      throw new MyException<caret>();
    }
  }
}