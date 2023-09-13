// "Replace with enhanced 'switch' statement" "true-preview"
import org.jetbrains.annotations.NotNull;

class X {
    private static void test(@NotNull Object object) {
        switch (object) {
            case Integer i ->
                // line contains no height
                    System.out.println(i + 1);
            case String s when !s.isEmpty() ->
                // line contains no code
                    System.out.println("Goodbye.");
            case null -> System.out.println("c");
            default -> System.out.println("default");
        }
    }
}