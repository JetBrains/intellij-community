public class Foo {
    int f(int x) {
        int i;
        i *= switch (x) {
            <caret>
        }
    }
}