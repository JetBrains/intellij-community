// "Convert 'x' to field in anonymous class" "true"
class Test {
    public void test() {
        int x = 12;
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
