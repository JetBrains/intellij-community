class A {
    void foo() {
        String s = null;
        try {
            s = "1";
            int invisibleOutsideTry = newMethod();
            s = s + invisibleOutsideTry;
        } catch (Exception e) {
            System.out.println("ex " + s);
        }
        System.out.println("ok " + s);
    }

    private int newMethod() throws Exception {
        int invisibleOutsideTry = 1 + 1;
        bar();
        return invisibleOutsideTry;
    }

    private void bar() throws Exception {
        throw new Exception();
    }
}