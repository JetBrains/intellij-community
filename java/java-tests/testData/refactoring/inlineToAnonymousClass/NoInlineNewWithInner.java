class <caret>WithInner {
    public class Inner {
    }
}

class A {
    public void test() {
        WithInner.Inner i = new WithInner().new Inner();
    }
}