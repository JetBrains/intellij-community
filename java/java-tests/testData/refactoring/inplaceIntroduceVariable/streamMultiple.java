import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().mapToInt(x -> x.length() + 10 * x.l<caret>ength()).forEach(System.out::println);
    }
}
