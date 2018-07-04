// "Move 'x' into anonymous object" "true"
class Test {
    public void test() {
        int x = 12;
        Runnable r = () -> {
            x<caret>++;
        };
    }
}
