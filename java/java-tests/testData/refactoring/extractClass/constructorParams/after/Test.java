class Test {
    final Extracted extracted = new Extracted(this);

    void bar(){
        extracted.bar();
    }

  String foo() {
    return "";
  }

  void bazz() {
      extracted.bar();
  }
}