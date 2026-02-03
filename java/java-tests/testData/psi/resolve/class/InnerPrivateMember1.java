class C{
  void foo(){
    class A{
      <caret>B b = null;
      private class B{};
    }
  }
}