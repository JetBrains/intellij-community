class B {
  void readFile(String s, Class c) {}

  {

      newMethod("foo");

      newMethod("bar");
  }

    private void newMethod(String foo) {
        readFile(foo, B.class);
        this.readFile(foo, B.class);
    }
}
