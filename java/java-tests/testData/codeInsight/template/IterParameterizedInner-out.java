import java.util.List;

public class LiveTemplateTest {

    void one() {
        List<A.B<String>> list;
        for (A.B<String> stringB : <selection>list</selection><caret>) {

        }
    }
}

class A {
    static interface B<T> {}
}