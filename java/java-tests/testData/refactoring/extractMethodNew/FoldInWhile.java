class Test {
    String[] foo() {
        return null;
    }

    boolean bar(String s) {
        return false;
    }

    void foooooo() {
        String[] modules = foo();
        int i = 0;
        while (i < modules.length && <selection>!bar(modules[i])</selection>) {
            i++;
        }
    }
}