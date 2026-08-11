// "Change 1st parameter of method 'annotatedParam()' from 'Integer' to 'String'" "true"
import javax.annotation.Nonnull;

class Hello {
  void foo() {
    annotatedParam("1");
  }

  void annotatedParam(@Nonnull String integer) {
    System.out.println(integer);
  }
}
