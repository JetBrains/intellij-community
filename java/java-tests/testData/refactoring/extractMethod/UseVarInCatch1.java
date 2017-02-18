class A {
    static class MyException extends Exception{ MyException(){ super(); } }

    void foo() {
        String s = "";
        try {
            s = "a";
            <selection>bar();
            s = "b";</selection>
            bar();
        } catch (MyException e) {
            throw new RuntimeException(s, e);
        }
    }

    void bar() throws MyException {
        if (true) throw new MyException();
    }
}