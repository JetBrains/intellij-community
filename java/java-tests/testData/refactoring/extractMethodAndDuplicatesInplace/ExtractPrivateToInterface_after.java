interface Test {

    class Nested {
        void test(){
            extracted();
        }
    }

    private static void extracted() {
        System.out.println();
    }
}