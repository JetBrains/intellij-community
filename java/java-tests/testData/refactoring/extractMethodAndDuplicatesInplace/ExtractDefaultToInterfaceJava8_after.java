interface Test {
    default void test(){
        extracted();
    }

    default void extracted() {
        System.out.println();
    }
}