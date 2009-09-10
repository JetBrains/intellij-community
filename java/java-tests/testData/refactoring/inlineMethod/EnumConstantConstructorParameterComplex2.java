public enum EEE {
    a(<caret>doTest("q"));

    EEE(String s) {
    }

    private static String doTest(String s) {
        System.out.println(s);
        return "";
    }
}
