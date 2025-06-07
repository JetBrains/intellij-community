package staticImportCanBeUsed;

import org.Foo2;

import static org.Foo3.*;

class Foo {
    void test(String[] baz) {
        Foo2.sort(baz);
        Foo2.sort(baz);
        System.out.println(PI);
    }
}