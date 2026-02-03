import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().forEach(s -> System.out.println(s.t<caret>oLowerCase()));
    }
}
