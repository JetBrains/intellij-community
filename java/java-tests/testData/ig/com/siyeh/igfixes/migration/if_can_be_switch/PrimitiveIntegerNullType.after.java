import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Integer l) {
        <caret>switch (l) {
            case 1 -> System.out.println("3");
            case 0 -> System.out.println("1");
            case Integer i -> System.out.println("2");
            case null -> {
            }
        }
    }
}