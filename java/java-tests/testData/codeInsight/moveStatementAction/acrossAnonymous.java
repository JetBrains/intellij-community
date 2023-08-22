public class Foo {
    public static int main(String[] args) {
        new Runnable() {
            public void run() {
                // TODO1: not implemented
            }
        };
        <caret>int r = 0;
        new Runnable() {
            public void run() {
                // TODO2: not implemented
            }
        };
        return r;
    }
}
