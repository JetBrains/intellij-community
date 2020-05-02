public class Test {
    public Object foo() {
        Object result = null;       // line1
        if (newMethod()) return result;
        if (test3()) return result;  // line4
        return result;
    }

    private boolean newMethod() {
        if (test1()) return true;
        if (test2()) return true;
        return false;
    }

    public int foo1() {
        Object result = null;       // line1
        if (newMethod()) return 1;
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