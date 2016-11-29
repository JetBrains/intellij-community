class A {
    void foo() {
        String s = "";
        try {
            <selection>s = "a";
            bar();</selection>
            s = "b";
        } catch (Exception e) {
            System.out.println(e);
        }
        System.out.println(s);
    }

    void bar() throws Exception {
        if (true) throw new Exception();
    }
}