import java.util.*;

class Foo {
    List<String> bar() {
        List<String> list = new ArrayList<>();
        for (var integer : in<caret>put) {
            list.add(integer == null ? null : integer.toString());
        }
        return Collections.unmodifiableList(list);
    }
}