class InnerStaticsJDK16
{
  class <warning descr="Inner class 'One' may be 'static'"><caret>One</warning> {
    class <warning descr="Inner class 'Two' may be 'static'">Two</warning> {}
  }
  public static void main(String[] args)
  {
    class Inner
    {
      class <warning descr="Inner class 'Nested' may be 'static'">Nested</warning>
      {}
    }
    new Object() {
      class <warning descr="Inner class 'Y' may be 'static'">Y</warning> {}
    };

  }
}
class One<A> {
  private int i;
  class Two<B> {
    private int j = i;
    class <warning descr="Inner class 'Three' may be 'static'">Three</warning><C> {}
  }

  public static void main(String[] args) {
    One<Void>.Two<Void>.Three<Void> x;
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