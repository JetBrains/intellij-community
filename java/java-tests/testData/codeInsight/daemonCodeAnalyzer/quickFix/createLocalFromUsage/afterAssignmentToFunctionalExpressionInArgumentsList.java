// "Create local variable 'lf'" "true-preview"
import java.util.function.Function;

class Main2 {
    void f(Function<String, String> g) {}
    {
        Function<String, String> lf;
        f(lf = c -> c);
    }
}
