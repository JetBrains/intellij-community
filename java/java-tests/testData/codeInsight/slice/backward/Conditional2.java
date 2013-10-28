class Test {

    public static void main(String[] <flown111211><flown11111>args) {
        final String a = <flown11121>args[0];
        final String b = <flown111>Test.class == null ? <flown1111>args[1] : <flown1112>a;
        new Runnable() {
            public void run() {
                System.out.println(f(a, <flown11>b));
            }
        }.run();
    }

    private static String f(String a, String <flown1>b) {
        return a + <caret>b;
    }

}
