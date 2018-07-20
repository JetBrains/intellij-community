// "Move 'x' into anonymous object" "true"
class Test {
    public void test() {
        var /*1*/ ref /*4*/ = new Object() {
            int /*6*/ x = 12;//2
            // 3
            int y = 23;
            //5
        };
        Runnable r = () -> {
            ref.x/*7*/++;
            ref.y++;
        };
    }
}
