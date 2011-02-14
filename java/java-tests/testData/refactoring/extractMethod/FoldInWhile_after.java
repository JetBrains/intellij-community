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
        while (i < modules.length && newMethod(modules[i])) {
            i++;
        }
    }

    private boolean newMethod(String module) {
        return !bar(module);
    }
}