// "Create constructor" "true"
class Test extends A{

    public Test(String a) {
        <selection>super(a);</selection>
    }

    public void t() {
        new Test("a"){};
    }
}

class A {
  A(String s){}
}