import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Integer l) {
        <caret>switch (l) {
            case 1 -> System.out.println("3");
            case 0 -> System.out.println("1");
            default -> System.out.println("2");
        }
    }
}