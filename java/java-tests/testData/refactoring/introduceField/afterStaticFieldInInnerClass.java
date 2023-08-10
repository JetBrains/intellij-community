public class A {
  private static final class Inner {
      public final String string;

      public Inner(Integer param) {
          string = param.toString();
     }
  }
}
