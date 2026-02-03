import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().map(s -> String.format("[%s]", s)).forEach(format -> System.out.println(
                new StringBuilder().append(format)
                        .append("oops")
                        .append("argh")
        ));
    }
}
