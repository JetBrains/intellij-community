public class AAA {
    <T> void g(T t) {
    }

    void g(String s) {
    }

    {
        <caret>g("");
    }
}
