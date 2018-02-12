// "Rename reference" "true"
class FooInterface {
  private int myInt;
}

class Foo {
    float myFloat;

    void buzz() {
        myI<caret>nt + myInt;
    }
}