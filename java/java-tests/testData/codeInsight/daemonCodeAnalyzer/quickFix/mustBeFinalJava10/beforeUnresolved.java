// "Move 'x' into anonymous object" "false"
class Test {
    public void test() {
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
