// "Create inner record 'Point'" "true"
public class Test {
    class Inner {
        void test(int x, int y) {
            new Po<caret>int(x, y);
        }
    }
}