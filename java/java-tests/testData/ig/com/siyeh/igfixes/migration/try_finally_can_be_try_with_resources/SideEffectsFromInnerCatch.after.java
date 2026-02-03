import java.io.IOException;
import java.io.InputStream;

class Test {
  void method(InputStream stream, InputStream stream2) {
    String sideEffect = "foo";
      try<caret> (stream) {
          try {
              System.out.println(sideEffect);
          } catch (Exception e) {
              System.out.println(sideEffect);
          }
      } catch (IOException e) {
          sideEffect = "bar";
      }
  }
}