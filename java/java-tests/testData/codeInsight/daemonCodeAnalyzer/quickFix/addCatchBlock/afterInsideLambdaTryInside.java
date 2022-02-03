// "Add 'catch' clause(s)" "true"
import java.io.IOException;
import java.util.function.Supplier;

class C {
  static Object get() throws Exception {
    return null;
  }

  void method() {
    try {
      Supplier<Object> lambda1 = () -> {
        try {
          return C.get();
        } catch (IOException e) {
          throw new RuntimeException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
      };
    } catch( Exception e) {
      throw new RuntimeException(e);
    }
  } 
}