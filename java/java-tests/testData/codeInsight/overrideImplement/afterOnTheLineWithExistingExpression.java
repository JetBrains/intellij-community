abstract class  Base  {
    void foo(){}
}

class Sub extends Base {
    int it;

    @Override
    void foo() {
        <caret><selection>super.foo();    //To change body of overridden methods use File | Settings | File Templates.</selection>
    }
}
