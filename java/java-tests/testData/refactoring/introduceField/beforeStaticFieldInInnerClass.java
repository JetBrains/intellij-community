public class A {
  private static final class Inner {
     public Inner(Integer param) {
        String s<caret>tr = param.toString();
     }
  }
}
