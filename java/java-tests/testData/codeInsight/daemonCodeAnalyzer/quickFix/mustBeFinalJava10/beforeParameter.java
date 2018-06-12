// "Move 'x' into anonymous object" "false"
class Test {
    public void test(int x) {
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
