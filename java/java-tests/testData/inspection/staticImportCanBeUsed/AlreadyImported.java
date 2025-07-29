package staticImportCanBeUsed;

import java.util.Arrays;
import static java.util.Arrays.sort;

class Foo {
    void test(String[] baz) {
        <warning descr="Static import can be used based on the auto-import table">Arrays<caret></warning>.sort(baz);
        <warning descr="Static import can be used based on the auto-import table">Arrays</warning>.sort(baz);
    }
}