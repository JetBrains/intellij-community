public class Foo {

    {
        Class<Foo> local;
        Class<? extends Foo> f = <caret>
    }

}

class Bar extends Foo {}
