class C {
    void f(boolean b, String s) {
        try {
            <selection>s = "a";
            if (b) throw new RuntimeException();</selection>
            s = "b";
        } catch (RuntimeException e) {
            System.out.println(s);
        }
    }
}