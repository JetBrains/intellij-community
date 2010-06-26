// "Change "1" to '1' (to char literal)" "false"
class Simple {
    Simple() {}
    Simple(int i) {}
    Simple(char ch) {}
    Simple(String s) {}

    public static void test() {
        final Simple instance = new Simple(<caret>"1", );
    }
}
