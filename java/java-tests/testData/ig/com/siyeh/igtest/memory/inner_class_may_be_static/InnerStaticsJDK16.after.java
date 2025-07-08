class InnerStaticsJDK16
{
  static class One {
    static class Two {}
  }
  public static void main(String[] args)
  {
    class Inner
    {
      static class Nested
      {}
    }
    new Object() {
      static class Y {}
    };

  }
}
class One<A> {
  private int i;
  class Two<B> {
    private int j = i;
    static class Three<C> {}
  }

  public static void main(String[] args) {
    One.Two.Three<Void> x;
  }
}
class C {
  void x(int i) {
    new Object() {
      class Local { // can't be static
        int get() {
          return i;
        }
      }
    };
  }
}