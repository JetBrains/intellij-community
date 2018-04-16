// "Convert 'x' to field in anonymous class" "false"
class Test {
    public void test(int x) {
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
