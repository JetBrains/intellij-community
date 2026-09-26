public class Test {
    public Object foo() {
        Object result = null;       // line1
        <selection>if (test1()) return result;
        if (test2()) return result;</selection>
        if (test3()) return result;  // line4
        return result;
    }

    public int foo1() {
        Object result = null;       // line1
        if (test1()) return 1; // ssss
        if (test2()) return 1; //eee
        if (test3()) return 2;  // line4
        return 3;
    }

    private boolean test1() {
        return false;
    }

    private boolean test2() {
        return false;
    }

    private boolean test3() {
        return false;
    }
}