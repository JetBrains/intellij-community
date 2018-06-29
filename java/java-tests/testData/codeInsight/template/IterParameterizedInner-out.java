import java.util.List;

public class LiveTemplateTest {

    void one() {
        List<A.B<String>> list;
        for (A.B<String> stringB : list) {

        }
    }
}

class A {
    static interface B<T> {}
}