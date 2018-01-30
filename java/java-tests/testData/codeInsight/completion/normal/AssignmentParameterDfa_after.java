import java.util.*;

class Foo {
    void test(List<String> list, boolean b) {
        if (b) {
            list = new ArrayList<>();
        }
        System.out.println(list.trimTo);
    }
}
