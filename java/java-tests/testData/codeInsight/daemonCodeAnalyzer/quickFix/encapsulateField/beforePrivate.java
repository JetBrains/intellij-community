// "Encapsulate field" "false"

class A {
  private boolean m_bool;
}

public class B {
  void method() {
    A a = new A();

    a.m_bo<caret>ol = true;
  }
}
