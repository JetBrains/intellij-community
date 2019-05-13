class Test {

    public static void main(String[] <flown1111>args) {
        final String a = <flown111>args[0];
        final String b = args[1];
        new Runnable() {
            public void run() {
                System.out.println(f(<flown11>a, b));
            }
        }.run();
    }

    private static String f(String <flown1>a, String b) {
        return <caret>a + b;
    }

}