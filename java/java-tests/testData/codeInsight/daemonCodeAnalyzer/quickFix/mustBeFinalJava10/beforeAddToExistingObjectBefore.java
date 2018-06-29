// "Move 'x' into anonymous object" "true"
class Test {
    public void test() {
        int /*6*/ x = 12;
        var /*1*/ ref /*4*/ = new Object() {//2
            // 3
            int y = 23;
            //5
        }
        Runnable r = () -> {
            <caret>x/*7*/++;
            ref.y++;
        };
    }
}
