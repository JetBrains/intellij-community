import java.io.IOException;
import java.io.InputStream;

class Test {
  void method(InputStream stream, InputStream stream2) {
    String sideEffect = "foo";
    try<caret> {
      System.out.println(sideEffect);
    } catch(Exception e) {
      System.out.println(sideEffect);
    }finally {
      try {
        stream.close();
      } catch (IOException e) {
        sideEffect = "bar";
      }
    }
  }
}