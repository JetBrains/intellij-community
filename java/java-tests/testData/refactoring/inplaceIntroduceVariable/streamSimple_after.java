import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().map(String::toLowerCase).forEach(lowerCase -> System.out.println(lowerCase));
    }
}
