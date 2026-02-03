import java.util.*;

class Foo {

    String[] foo();
    String[] bar();

    static {
        Foo f;
        List<String> g = f.<caret>
    }
}