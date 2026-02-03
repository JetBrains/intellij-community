class C {
  interface Simplest {
    void m();
  }
  void simplest() { }
  void use(Simplest s) { }

  void test() {
    Simplest simplest = this::simplest;
    use(this::simplest);
  }
}