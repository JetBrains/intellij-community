import test.*;

public class A {
    public static class B extends ListList<Integer> {
    }

    public void method() {
        ListList<Integer> l = new ListList<Integer>();
        l.get(0);
        B b = new B();
        b.get(0);
        l.add(b.get(0));
        b.add(l.get(0))
    }
}
