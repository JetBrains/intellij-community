import java.util.Collections;

class Test {

  private static String useAndReturnValue(final String result) {
    Collections.singleton(result);
    return result;
  }

  void m() {
    useAndReturnValue(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }
}