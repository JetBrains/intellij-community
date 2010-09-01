public class Clas extends Clas2{
    int foo;
    <caret>
}

class Clas2 {
    int foo;

    Clas2(int foo) {
        this.foo = foo;
    }
}
