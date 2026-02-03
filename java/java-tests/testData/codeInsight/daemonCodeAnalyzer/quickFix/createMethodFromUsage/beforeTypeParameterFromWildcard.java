// "Create method 'test'" "true-preview"
import java.util.List;
public class Test {
    <T> void f(List<? extends T> l)  {
        te<caret>st(l);
    }
}
