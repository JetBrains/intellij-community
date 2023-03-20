interface Test {

    class Nested {
        void test(){
            extracted();
        }
    }

    static void extracted() {
        System.out.println();
    }
}