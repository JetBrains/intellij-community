import org.jetbrains.annotations.Nullable;

class Test {
    void test(int l) {
        <caret>switch (l) {
            case int j when 1 > j -> System.out.println("3");
            case int j when 0 < j -> System.out.println("1");
            case Integer i -> System.out.println("2");
        }
    }
}