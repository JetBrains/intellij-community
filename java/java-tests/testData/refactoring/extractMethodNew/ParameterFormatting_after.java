class Test {
    void foo(String[] ss) {
        int x = 42;

        newMethod(x);

    }

    private void newMethod(int x) {
        System.out.println(x+1);
    }
}