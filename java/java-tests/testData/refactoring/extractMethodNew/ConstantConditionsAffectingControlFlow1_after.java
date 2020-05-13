class Test {

    public void test(boolean b) {
        int a = 1;
        if (true) {
            newMethod(a);
        } else {
            newMethod(a);
        }
    }

    private void newMethod(int a) {
        System.out.println(a);
    }


}