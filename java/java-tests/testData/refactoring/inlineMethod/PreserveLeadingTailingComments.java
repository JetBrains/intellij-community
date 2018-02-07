class Test {
    public static void main(String... args) {
        methodToBe<caret>Inlined1();

    }

    private static void methodToBeInlined1() {
        // This comment will be lost after inlining this method.
        System.out.println("Hello, bug! (1)");
        /*
         * This comment will be lost after inlining this method.
         */
    }
}