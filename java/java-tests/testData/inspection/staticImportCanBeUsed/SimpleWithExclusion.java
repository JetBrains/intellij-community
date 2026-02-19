package staticImportCanBeUsed;

import java.util.Arrays;

class Foo {
    void test(String[] baz) {
        Arrays<caret>.sort(baz);
        Arrays.sort(baz);
    }
}