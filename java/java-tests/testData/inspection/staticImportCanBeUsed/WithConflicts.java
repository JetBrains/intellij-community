package staticImportCanBeUsed;

import java.util.Arrays;

class Foo {
    void test(String[] baz) {
        Arrays.sort(baz);
        Arrays.sort(baz);
    }

    public static void sort(String[] a) {}
}