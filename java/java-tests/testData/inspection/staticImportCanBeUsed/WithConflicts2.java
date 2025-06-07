package staticImportCanBeUsed;

import java.util.Arrays;

import static org.Foo2.*;

class Foo {
    void test(String[] baz) {
        Arrays.sort(baz);
        Arrays.sort(baz);
        binarySearch(baz, "a");
    }
}