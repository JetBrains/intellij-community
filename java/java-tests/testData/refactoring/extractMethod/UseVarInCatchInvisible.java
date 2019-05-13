class A {
    void foo() {
        String s = null;
        try {
            s = "1";
            <selection>int invisibleOutsideTry = 1 + 1;
            bar();</selection>
            s = s + invisibleOutsideTry;
        } catch (Exception e) {
            System.out.println("ex " + s);
        }
        System.out.println("ok " + s);
    }

    private void bar() throws Exception {
        throw new Exception();
    }
}