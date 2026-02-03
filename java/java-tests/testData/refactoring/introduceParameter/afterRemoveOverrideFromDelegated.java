interface Foo {
  void foobalize();
}

class ExtractTest implements Foo {
    @Override
    public void foobalize() {
        foobalize(42);
    }

    public void foobalize(int anObject) {
    System.out.println(anObject);
  }
}