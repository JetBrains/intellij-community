interface Foo {
  int ABC = 1;
}

interface Bar extends Foo {
  boolean ABC = 2;
}

class FooImpl implements Foo {}

class BarImpl extends FooImpl implements Bar {
  {
    boolean a = Bar.ABC;<caret>
  }
}