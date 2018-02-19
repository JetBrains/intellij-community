import java.util.*;

class Foo {
    void test(boolean b) {
        List<String> list = new ArrayList<>();
        list.add("foo");
        if (b) {
            list = new ArrayList<>();
        }
        System.out.println(list.trimTo<caret>);
    }
}
