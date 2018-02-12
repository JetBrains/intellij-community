// "Create constructor" "false"
public class Test {

  @interface A {}

  void usage() {
    new A(<caret>"a") {
      @Override
      public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return null;
      }
    };
  }
}
