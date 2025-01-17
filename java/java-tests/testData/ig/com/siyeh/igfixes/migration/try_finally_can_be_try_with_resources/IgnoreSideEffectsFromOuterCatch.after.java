import java.io.IOException;
import java.io.InputStream;

class Test {
  void method(InputStream stream) {
    String sideEffect = "foo";
      try<caret> (stream) {
          stream.read()
      } catch (IOException e) {
          System.out.println(sideEffect);
      } catch (Exception e) {
          sideEffect = "bar";
      }
  }
}