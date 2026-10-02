final class F {
    private final int x;

    F(int x) {
        this.x = x;
    }

    public int getX() {
        return x;
    }

    public int square() {
        return x * x;
    }
}
class G {
    static void main() {
        F f = new F(10);
        int square = f.squ<caret>are();
    }
}