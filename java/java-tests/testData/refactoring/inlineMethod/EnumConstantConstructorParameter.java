public enum EEE {
    a(<caret>doTest());

    EEE(String s) {
    }

    private static String doTest() {
        return "";
    }
}
