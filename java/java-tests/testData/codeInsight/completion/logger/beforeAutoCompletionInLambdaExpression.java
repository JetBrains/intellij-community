import java.util.function.Consumer;

public class A {
    void foo() {
        Consumer<String> s = (param) -> lo<caret>
    }
}