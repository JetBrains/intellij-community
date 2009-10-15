class BarBase {

    {
        boolean foo;
        boolean f = !Foo.foo;<caret>
    }

}

class Foo {
  static final Boolean foo;
}