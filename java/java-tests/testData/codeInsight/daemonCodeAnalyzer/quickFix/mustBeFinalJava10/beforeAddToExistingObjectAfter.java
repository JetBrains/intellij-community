// "Move 'x' into anonymous object" "true-preview"
class Test {
    public void test() {
        var ref = new Object() {
            int y = 23;
        };
        int x = 12;
        Runnable r = () -> {
            <caret>x++;
            ref.y++;
        };
    }
}
