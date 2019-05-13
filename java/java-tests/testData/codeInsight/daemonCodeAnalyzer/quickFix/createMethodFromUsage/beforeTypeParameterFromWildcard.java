// "Create method 'test'" "true"
import java.util.List;
public class Test {
    <T> void f(List<? extends T> l)  {
        te<caret>st(l);
    }
}
