// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class X {
    void test(List<String> list, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException();
        }
        list.subList(from, to + 1).clear();
    }
}
