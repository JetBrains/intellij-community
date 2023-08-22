// "Create inner record 'Point'" "true-preview"
public class Test {
    class Inner {
        void test(int x, int y) {
            new Po<caret>int(x, y);
        }
    }
}