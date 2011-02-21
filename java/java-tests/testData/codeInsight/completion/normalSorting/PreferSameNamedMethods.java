class Goo {
    int boo() {}
    int doo() {}
    int foo() {}
}

class Foooo {
    Goo g;

    int foo() {
        return g.<caret>
    }

}

