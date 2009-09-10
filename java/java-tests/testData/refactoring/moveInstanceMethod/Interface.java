interface Foreign {
}

class ForeignImpl implements Foreign {
}


public abstract class Test1 {
    int field;

    void <caret>foo (Foreign f) {
        field++;
    }

    void bar () {
        foo(new ForeignImpl());
    }
}