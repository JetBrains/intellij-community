class Foo {
    {
        Object _o;
        Bar b;
        if (_o instanceof Goo) {
            Goo g = <caret>
        }
    }
}

class Goo {

}

class Bar extends Goo {

}