class B {
    public class Inner {
    }
}

class A extends B {}

class Client {
    public B.Inner getInner() {
        return null;
    }
}
