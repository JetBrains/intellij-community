import java.io.IOException;
import java.io.InputStream;

class Test {
  void method(InputStream stream) {
    String sideEffect = "foo";
      try<caret> (stream) {
          try {
              stream.read()
          } catch (Exception e) {
              sideEffect = "bar";
          }
      } catch (IOException e) {
          System.out.println(sideEffect);
      }
  }
}