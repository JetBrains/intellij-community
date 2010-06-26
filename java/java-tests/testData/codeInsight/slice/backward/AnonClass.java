class Test {

    public static void main(String[] args) {
        final String a = <flown11>args[0];
        final String b = args[1];
        new Runnable() {
            public void run() {
                System.out.println(f(<flown1>a, b));
            }
        }.run();
    }

    private static String f(String a, String b) {
        return <caret>a + b;
    }

}