// "Create inner record 'Point'" "true"
public class Test {
    class Inner {
        void test(int x, int y) {
            new Point(x, y);
        }
    }

    private record Point(int x, int y) {
    }
}