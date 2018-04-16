// "Convert 'x' to field in anonymous class" "true"
class Test {
    public void test() {
        var ref = new Object() {
            int x = 12;
            int y = 23;
        };
        Runnable r = () -> {
            ref.x++;
            ref.y++;
        };
    }
}
