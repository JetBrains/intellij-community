// "Convert 'x' to field in anonymous class" "false"
class Test {
    public void test() {
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
