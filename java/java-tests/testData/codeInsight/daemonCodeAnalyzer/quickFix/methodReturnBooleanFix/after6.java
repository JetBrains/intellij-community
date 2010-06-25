// "Make 'm1' return 'boolean'" "true"
interface A {
    boolean m1();
}

class B {
    public boolean m1() {
        boolean candidate;
        return candidate;
    }
}

class C extends B implements A {
    private final boolean myBoolean;

    public boolean m1() {
        return false;
    }
}

public class D extends C {
    public void user() {
        while (m1()) {
            System.out.println("m1");
        }
    }

    public boolean m1() {
        return <caret><selection>false</selection>;
    }
}