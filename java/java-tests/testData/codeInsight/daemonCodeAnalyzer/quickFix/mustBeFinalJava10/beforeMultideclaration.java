// "Move 'x' into anonymous object" "false"
class Test {
    void test() {
        int x = 1, y = 2;
        Runnable r = () -> <caret>x++;
    }
}
