import java.io.IOException;
import java.io.InputStream;

class Test {
  void method(InputStream stream) {
    String sideEffect = "foo";
    try<caret> {
      stream.read()
    } catch (Exception e) {
      sideEffect = "bar";
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
        System.out.println(sideEffect);
      }
    }
  }
}