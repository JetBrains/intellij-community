interface Foo {
  void foobalize();
}

class ExtractTest implements Foo {
  @Override
  public void foobalize() {
    System.out.println(<selection>42</selection>);
  }
}