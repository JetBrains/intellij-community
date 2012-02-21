// "Override method 'foo'" "true"
class Test {
  protected void foo(){}
}

class TImple extends Test {
    @Override
    protected void foo() {
        <selection>super.foo();    //To change body of overridden methods use File | Settings | File Templates.</selection>
    }
}