package staticImportCanBeUsed;

import java.util.Arrays;

class Foo {
    void test(String[] baz) {
        <warning descr="On-demand static import can be used">Arrays</warning><caret>.sort(baz);
        <warning descr="On-demand static import can be used">Arrays</warning>.sort(baz);
    }
}