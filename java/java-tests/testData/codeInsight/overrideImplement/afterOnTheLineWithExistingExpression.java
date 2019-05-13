abstract class  Base  {
    void foo(){}
}

class Sub extends Base {
    int it;

    @Override
    void foo() {
        <caret><selection>super.foo();</selection>
    }
}
