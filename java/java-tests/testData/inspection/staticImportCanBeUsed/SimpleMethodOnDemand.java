package staticImportCanBeUsed;

import java.util.Arrays;

class Foo {
    void test(String[] baz) {
        <warning descr="Static import can be used">Arrays<caret></warning>.sort(baz);
        Arrays.binarySearch(baz, "1");
    }
}