import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        int f = 42;
        <caret>if (o instanceof String keepName) {
            System.out.println();
        } else if (o instanceof Integer i) {
            System.out.println();
        } else if (o instanceof Float f1) {
            System.out.println();
        } else {
        }
        int i = 42;
    }
}