class A {
    public void foo() {
        new Runnable() {
            @Override
            public void run() {<caret>
            }
        }
    }
}