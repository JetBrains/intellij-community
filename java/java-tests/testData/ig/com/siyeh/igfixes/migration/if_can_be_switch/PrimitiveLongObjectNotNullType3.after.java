import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Long l) {
        <caret>switch (l) {
            case 1L -> System.out.println("3");
            case 0L -> System.out.println("1");
            default -> System.out.println("2");
        }
    }
}