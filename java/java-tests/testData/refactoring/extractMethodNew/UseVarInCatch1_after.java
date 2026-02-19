import org.jetbrains.annotations.NotNull;

class A {
    static class MyException extends Exception{ MyException(){ super(); } }

    void foo() {
        String s = "";
        try {
            s = "a";
            s = newMethod(s);
            bar();
        } catch (MyException e) {
            throw new RuntimeException(s, e);
        }
    }

    private @NotNull String newMethod(String s) throws MyException {
        bar();
        s = "b";
        return s;
    }

    void bar() throws MyException {
        if (true) throw new MyException();
    }
}