class Test
{
    public Object foo() {
        Object result = null;
        if (newMethod()) return null;
        return result;
    }

    private boolean newMethod() {
        if (test1()) return true;
        if (test2()) return true;
        return false;
    }

    public int foo1() {
        int result = 1;
        if (newMethod()) return 0;
        return result;
    }

    private boolean test1() {return false;}
    private boolean test2() {return false;}
}