public class Util {
  public static void invokeService(Service s) {
    s.foo();
  }

  public static void invokeDerivedService(DerivedService s) {
    s.foo();
  }
}