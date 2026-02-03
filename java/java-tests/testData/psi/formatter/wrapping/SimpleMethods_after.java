
public class Foo {
    public int foo() { return 5; }

    public int longerMethodFooHere() { return thisCodeBlockShouldBeWrapped; }

    int bar() {
        st = 2;
        st = 3;
    }

    int foobar() { //aaaaaaaaaaaaaa
        st = 2;
        st = 3;
    }

    int barbar() { }
}