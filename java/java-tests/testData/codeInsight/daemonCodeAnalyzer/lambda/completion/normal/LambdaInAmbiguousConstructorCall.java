import java.util.function.Function;
class TestMain {
    public static void main(String[] args) {
        new TestMain("abc", url -> url.<caret>);
    }

    TestMain(String str, Function<String, String> a, Function<String, String> b) {}

    TestMain(int i) {}
}
