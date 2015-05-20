// "Rename reference" "true"
class FooInterface {
  private int myInt;
}

class Foo {
    void buzz() {
        myI<caret>nt + myInt;
    }
}