import java.util.List;

public class LiveTemplateTest {

    void one() {
        List<A.B<String>> list;
        <caret>
    }
}

class A {
    static interface B<T> {}
}