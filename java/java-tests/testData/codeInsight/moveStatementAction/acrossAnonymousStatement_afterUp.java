public class Foo {
    public static int main(String[] args) {
        <caret>int r = 0;
        Object o1 = new Runnable() {
            public void run() {
                // TODO1: not implemented
            }
        };
        Object o2 = new Runnable() {
            public void run() {
                // TODO2: not implemented
            }
        };
        return r;
    }
}
