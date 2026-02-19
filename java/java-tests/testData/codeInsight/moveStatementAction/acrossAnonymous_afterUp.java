public class Foo {
    public static int main(String[] args) {
        <caret>int r = 0;
        new Runnable() {
            public void run() {
                // TODO1: not implemented
            }
        };
        new Runnable() {
            public void run() {
                // TODO2: not implemented
            }
        };
        return r;
    }
}
