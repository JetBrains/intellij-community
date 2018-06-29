// "Move 'x' into anonymous object" "false"
class Test {
    public void test() {
        int x = 12;
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
