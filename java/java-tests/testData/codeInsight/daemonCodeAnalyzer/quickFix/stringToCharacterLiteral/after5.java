// "Change "1" to '1' (to char literal)" "true"
class Simple {
    Simple() {}
    Simple(int i) {}
    Simple(char ch) {}

    public static void test() {
        final Simple instance = new Simple(<caret>'1');
    }
}
