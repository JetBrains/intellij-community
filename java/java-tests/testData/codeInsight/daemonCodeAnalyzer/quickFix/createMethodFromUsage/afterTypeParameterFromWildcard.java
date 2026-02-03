// "Create method 'test'" "true-preview"
import java.util.List;
public class Test {
    <T> void f(List<? extends T> l)  {
        test(l);
    }

    private <T> void test(List<? extends T> l) {
        <caret>
    }
}
