class A {
    static class MyException extends Exception{ MyException(){ super(); } }

    void foo() {
        String s = "";
        try {
            <selection>s = "a";
            bar();</selection>
            s = "b";
            bar();
        } catch (MyException e) {
            throw new RuntimeException(s, e);
        }
    }

    void bar() throws MyException {
        if (true) throw new MyException();
    }
}