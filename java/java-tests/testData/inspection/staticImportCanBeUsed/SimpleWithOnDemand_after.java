package staticImportCanBeUsed;

import static java.util.Arrays.*;

class Foo {
    void test(String[] baz) {
        <caret>sort(baz);
        sort(baz);
    }
}