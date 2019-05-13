interface Root1 {
    A get();
}
interface Root2 extends Root1 {
    B get();
}

interface Child extends Root1, Root2 {
}

public class Test {
    public void test(Child child) {
        child.get<caret>x
    }
}

interface A {}
interface B extends A {}