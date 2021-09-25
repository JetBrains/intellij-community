public class OverloadedMember3 {
  class C1 {
    void f(String s) {
      System.err.println(s);
    }
  }

  class C2 extends C1 {
    @Override
    void f(String <flown1>s) {
      System.out.println(<flown11>s);
    }

    void g() {
      f(<caret>"A");
    }
  }

  class C3 extends C2 {
    @Override
    void f(String <flown2>s) {
      System.out.println(<flown21>s);
    }
  }
}