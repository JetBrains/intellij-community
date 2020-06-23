// "Add 'Exception' as 1st parameter to method 'f'" "true"
import java.io.FileInputStream;
import java.io.IOException;

class Test {
  public void createFileInputStream() {
    try {
      new FileInputStream("test");
    } catch (IOException |NullPointerException e) {
      f(e);
    }
  }

  public void f(Exception e) { }
}
