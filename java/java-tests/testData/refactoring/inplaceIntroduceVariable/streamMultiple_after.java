import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().mapToInt(String::length).map(length -> length + 10 * length).forEach(System.out::println);
    }
}
