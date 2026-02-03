class Test {
    void foo () {
        int a = 1;
        newMethod((Object) a);
    }

    private void newMethod(Object a) {
        System.out.println("" + a);
    }
}