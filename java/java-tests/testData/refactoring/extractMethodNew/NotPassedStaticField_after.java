class Test {
    int local = 42;
    static int global = 42;

    void test(){
        newMethod(local);
    }

    private static void newMethod(int local) {
        System.out.println(local + global);
    }
}