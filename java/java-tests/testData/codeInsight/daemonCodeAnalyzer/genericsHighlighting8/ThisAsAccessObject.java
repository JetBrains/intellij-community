class Outer {
    private void foo() {}
    class Inner extends Outer {
        {
           this.<error descr="'foo()' has private access in 'Outer'">foo</error>();
           foo();
        }
    }
}
