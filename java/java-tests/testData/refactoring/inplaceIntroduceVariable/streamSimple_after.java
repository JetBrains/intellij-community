import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().map(String::toLowerCase).forEach(s1 -> System.out.println(s1));
    }
}
