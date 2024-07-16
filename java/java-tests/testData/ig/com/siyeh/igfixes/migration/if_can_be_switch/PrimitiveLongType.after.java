import org.jetbrains.annotations.Nullable;

class Test {
    void test(long l) {
        <caret>switch (l) {
            case 1L -> System.out.println("3");
            case 0L -> System.out.println("1");
            case int i -> System.out.println("2");
            default -> {
            }
        }
    }
}