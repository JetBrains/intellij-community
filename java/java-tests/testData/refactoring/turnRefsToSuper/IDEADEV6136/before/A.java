class B {
    public class Inner {
    }
}

class A extends B {}

class Client {
    public A.Inner getInner() {
        return null;
    }
}
