// "Add 'catch' clause(s)" "false"
import java.util.function.Supplier;

class C {
  static Object get() throws Exception {
    return null;
  }

  void method() {
    try {
      Supplier<Object> lambda1 = () -> C.ge<caret>t();
    } catch( Exception e) {
      throw new RuntimeException(e);
    }
  } 
}