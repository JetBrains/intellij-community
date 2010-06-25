package x;
public class A extends D{
    int k;
    static void staticF() {}
}
class C extends x.sub.B {
    int n=<error descr="'k' is not public in 'x.A'. Cannot be accessed from outside package">k</error>;

    // can call despite inherited through package local from other package
    void f() { A.staticF(); }
}

class D{
    public void foo(){}
}
