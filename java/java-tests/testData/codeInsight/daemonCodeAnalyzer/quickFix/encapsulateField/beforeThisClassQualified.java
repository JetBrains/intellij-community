// "Encapsulate field" "false"

class A {
  public final boolean m_bool;

  void method() {
    this.m_bo<caret>ol = true;
  }
}
