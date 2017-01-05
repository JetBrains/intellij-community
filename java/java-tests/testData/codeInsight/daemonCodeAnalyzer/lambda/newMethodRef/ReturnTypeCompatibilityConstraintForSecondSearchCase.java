
class Test {
  public interface Copier<TypeT> {
    TypeT m(TypeT value);
  }

  public static class A<PhantomT> {
    public <OtherT extends A<?>> OtherT copy() {
      return null;
    }
  }

  static <TypeT> void foo(TypeT value, Copier<TypeT> copier) {}

  public static void foo() {
    A<?> val = new A<>();
    foo(val, A::copy);
  }
}