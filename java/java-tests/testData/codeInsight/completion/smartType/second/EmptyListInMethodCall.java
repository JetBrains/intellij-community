import java.util.List;

public class SomeClass {
    void foo(List<String> l) {

    }

    {
        foo(em<caret>)
    }

}
