import org.jetbrains.annotations.Nullable;

class Test {
    void test(int l) {
        <caret>switch (l) {
            case int j when j > 1 -> System.out.println("3");
            case int j when j < 0 -> System.out.println("1");
            case Integer i -> System.out.println("2");
        }
    }
}