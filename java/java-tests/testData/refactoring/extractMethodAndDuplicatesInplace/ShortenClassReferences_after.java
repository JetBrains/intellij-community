import java.util.ArrayList;
import java.util.stream.Stream;

class Test {
    void test() {
        var s = new ArrayList<>().stream();
        extracted(s);
    }

    private void extracted(Stream<Object> s) {
        System.out.println(s);
    }
}