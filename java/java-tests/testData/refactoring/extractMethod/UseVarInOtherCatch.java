class A {
    static class ExceptionA extends Exception{ ExceptionA(){ super(); } }
    static class ExceptionB extends Exception{ ExceptionB(){ super(); } }

    void foo(boolean a, boolean b) {
        String s = "";
        try {
            s = "a";
            if (a) throw new ExceptionA();
            <selection>s = "b";
            if (b) throw new ExceptionB();
            s = "c";</selection>
            System.out.println(s);
        } catch (ExceptionA ea) {
            throw new RuntimeException(s, ea);
        } catch (ExceptionB eb) {
            System.out.println(eb);
        }
    }
}