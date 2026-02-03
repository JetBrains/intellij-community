public class OverloadedMember2 {
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
}