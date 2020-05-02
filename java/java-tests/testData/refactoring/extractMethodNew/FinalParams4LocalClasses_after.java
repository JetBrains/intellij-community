class Test {

    public static void main(String[] args) {
        final String s = "text";
        newMethod(s);
    }

    private static void newMethod(final String s) {
        class A {
            {
                System.out.println(s);
            }
        }
    }
}