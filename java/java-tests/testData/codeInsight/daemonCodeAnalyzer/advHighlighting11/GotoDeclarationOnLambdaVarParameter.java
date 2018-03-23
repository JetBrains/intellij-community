import java.util.function.Function;

class Main {
    public static void main(String[] args) {
        Function<String, String> f = (final v<caret>ar a) -> a;
    }
}
