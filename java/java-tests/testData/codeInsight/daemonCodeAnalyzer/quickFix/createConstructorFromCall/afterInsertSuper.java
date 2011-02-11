// "Create Constructor" "true"
class Test extends A{

    public Test(String a) {
        <selection>super(a);    //To change body of overridden methods use File | Settings | File Templates.</selection>
    }

    public void t() {
        new Test("a"){};
    }
}

class A {
  A(String s){}
}