public class FooBar {

 class Xxx {}

    Xxx foo() { }
    Xxx bar() { }
    Xxx goo() { }

    void aaa(Xxx a) {}

    {
        new FooBar().aaa( new FooBar().<caret> );
    }


}