public class Clas extends Clas2{
    int foo;

    public Clas(int foo, int foo1) {<caret>
        super(foo);
        this.foo = foo1;
    }
}

class Clas2 {
    int foo;

    Clas2(int foo) {
        this.foo = foo;
    }
}
