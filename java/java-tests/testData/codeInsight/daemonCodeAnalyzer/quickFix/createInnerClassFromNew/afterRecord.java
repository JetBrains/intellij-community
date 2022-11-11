// "Create inner record 'Foo'" "true-preview"
public class Test {
    public static void main() {
        new Foo("bar", "baz")
    }

    private record Foo(String bar, String baz) {
    }
}