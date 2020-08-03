interface Test {
    int implicitStatic = 42;

    default void test(){
        newMethod();
    }

    private static void newMethod() {
        System.out.println(implicitStatic);
    }
}