import java.util.Objects;

class Test {

  void dangerousMethod(String reassginedParameter) {
    if (reassginedParameter == null) {
      reassginedParameter = "default value";
    }
    Objects.requireNonNull(reassginedParameter);
    System.out.println(reassginedParameter);
  }

  void usage() {
    dangerousMethod(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }

}