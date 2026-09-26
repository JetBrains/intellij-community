interface I {
  void m(int i);
}
class B {
    private static void m(int i1) {
        System.out.println(i1 + "");
    }

    void m() {}
  {
    I i = B::m;
  }
}