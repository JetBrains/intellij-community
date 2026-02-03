import java.util.ArrayList;

class A {
  void m() {
    class B {}
    java.util.List<B> l = new ArrayList<B>(<caret>);
  }
}