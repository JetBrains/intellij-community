import java.util.*;
class Test {
    private static <T> List<T> <caret>foo() { return Collections.emptyList(); }
    private static <T> List<T> bar(List<T> xs) { return xs; }
    private static void gazonk() {
        List<String> ss = bar(Test.<String>foo());
    }
}
