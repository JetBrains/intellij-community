interface Foo extends Comparable {
    void compareTo(Foo foo);
}

class User {
    void foo (Foo foo) {
        foo.<caret>compareTo(foo);
    }
}