// "Replace with qualifier" "true"
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

class Main {
    void test(List<List<String>> list) {
        Function<List<String>, Stream<String>> fn = List::stream;
        System.out.println(list.stream().flatMap(fn).count());
    }
}