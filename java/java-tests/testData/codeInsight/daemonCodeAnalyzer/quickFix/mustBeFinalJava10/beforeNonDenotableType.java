// "Move 'x' into anonymous object" "false"
class Test {
    public void test() {
        var x = new Object() {
          int y = 12;
        }
        Runnable r = () -> {
            x<caret> = null;
        };
    }
}
