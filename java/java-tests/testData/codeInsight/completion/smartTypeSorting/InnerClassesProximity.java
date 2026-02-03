package zzzzz;

public class Foo {
    {
        class InnerGoo extends Goo{}

        Goo g = new <caret>
    }

    class Bar extends Goo {

    }

}

class Goo {

}

class AGoo extends Goo {}

