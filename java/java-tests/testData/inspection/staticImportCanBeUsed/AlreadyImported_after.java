package staticImportCanBeUsed;

import static java.util.Arrays.sort;

class Foo {
    void test(String[] baz) {
        <caret>sort(baz);
        sort(baz);
    }
}