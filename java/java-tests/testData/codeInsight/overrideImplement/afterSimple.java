abstract class  IX  {
    void foo(){}
}

class XXC extends IX {
    @Override
    void foo() {
        <caret><selection>super.foo();</selection>
    }
}
