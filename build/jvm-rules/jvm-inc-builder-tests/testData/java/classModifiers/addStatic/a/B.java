public class B {
    public A.B get () {
        return new A(1).new B(3);
    }
}