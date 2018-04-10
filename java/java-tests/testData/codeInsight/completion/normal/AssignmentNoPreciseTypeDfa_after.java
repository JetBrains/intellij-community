import java.util.*;

class Foo {
    void test(boolean b) {
        List<String> list = new ArrayList<>();
        if (b) {
            list = Collections.emptyList();
        }
        System.out.println(list.trimTo);
    }
}
