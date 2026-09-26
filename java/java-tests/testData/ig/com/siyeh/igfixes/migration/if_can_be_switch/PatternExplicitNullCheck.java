import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Object o) {
        <caret>if (o instanceof String) {
            System.out.println();
        } else if (o instanceof Integer || o == null) {
            System.out.println();
        } else {
        }
    }
}