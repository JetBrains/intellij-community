class s {
  void f() {
    int i;
    int <error descr="Variable 'i' is already defined in the scope">i</error>;
  }
  void f1() {
    int i;
    {
      int <error descr="Variable 'i' is already defined in the scope">i</error>;
    }
  }
  void f2() {
    int i;
    class ss
    {
      int i;
    }
  }

  int f;
  int <error descr="Variable 'f' is already defined in the scope">f</error>;
  void f3() {
    int f;
  }
}