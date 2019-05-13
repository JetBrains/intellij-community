public class Test {
    int i;

    public void foo(int j) {
        foo(this, j);
    }

    public static void foo(Test anObject, int j) {
    }
}