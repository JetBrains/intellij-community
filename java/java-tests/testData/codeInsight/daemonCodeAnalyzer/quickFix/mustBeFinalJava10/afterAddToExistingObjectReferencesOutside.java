// "Convert 'x' to field in anonymous class" "true"
class Test {
    public void test() {
        var ref1 = new Object() {
            int x = 12;
        };
        var ref = new Object() {
            int y = 23;
        }
        ref.y++;
        Runnable r = () -> {
            ref1.x++;
            ref.y++;
        };
    }
}
