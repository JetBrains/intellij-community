// "Make 'm1()' return 'boolean'" "true-preview"
interface A {
    void m1();
}

class B {
    public void m1() {
        boolean candidate;
    }
}

class C extends B implements A {
    private final boolean myBoolean;

    public void m1() {
    }
}

public class D extends C {
    public void user() {
        while (m1()) {
            System.out.println("m1");
        }
    }

    public boolean m1() {
        return false;
    }
}