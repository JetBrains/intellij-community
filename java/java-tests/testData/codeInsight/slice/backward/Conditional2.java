class Test {

    public static void main(String[] args) {
        final String a = <flown1121>args[0];
        final String b = <flown11>Test.class == null ? <flown111>args[1] : <flown112>a;
        new Runnable() {
            public void run() {
                System.out.println(f(a, <flown1>b));
            }
        }.run();
    }

    private static String f(String a, String b) {
        return a + <caret>b;
    }

}
