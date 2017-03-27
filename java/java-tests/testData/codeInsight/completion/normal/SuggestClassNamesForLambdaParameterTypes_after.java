package abcdef;

import java.util.List;

/**
 * Created by peter on 1/27/17. ${FFF}
 */
public class Foo {
    void foo() {
        List<String> strings = null;
        strings.stream().anyMatch((CharSequence<caret>x) -> x.charAt(0) == 'a');
    }
}
