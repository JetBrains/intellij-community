import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Long l) {
        <caret>switch (l) {
            case 1L -> System.out.println("3");
            case 0L -> System.out.println("1");
            case Integer i -> System.out.println("2");
            case null, default -> {
            }
        }
    }
}