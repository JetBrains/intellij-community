// "Convert 'x' to field in anonymous class" "true"
class Test {
    public void test() {
        int x = 12;
        var ref = new Object() {
            int y = 23;
        }
        Runnable r = () -> {
            <caret>x++;
            ref.y++;
        };
    }
}
