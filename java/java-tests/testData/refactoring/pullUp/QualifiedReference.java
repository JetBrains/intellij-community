class X {}

class <caret>Y extends X {
    private static int x = 0;

    public static int getX() {
        return x;
    }

    public static void setX(int x) {
        Y.x = x;
    }
}