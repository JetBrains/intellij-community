// "Create field for parameter 's'" "true"

record R() {
    private static String s;

    static void x(String s) {
        R.s = s;
    }
}