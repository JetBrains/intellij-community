import java.util.*;

class Foo {

    String[] foo();
    String[] bar();

    static {
        Foo f;
        List<String> g = Arrays.asList(f.foo());<caret>
    }
}