// "Replace with enhanced 'switch' statement" "true"
import org.jetbrains.annotations.NotNull;

class X {
    private static void test(@NotNull Object object) {
        switch (object) {
            case Integer i ->
                // line contains no height
                    System.out.println(i + 1);
            case String s && !s.isEmpty() ->
                // line contains no code
                    System.out.println("Goodbye.");
            case null -> System.out.println("c");
            default -> System.out.println("default");
        }
    }
}