import java.util.List;

public class StreamExtract {
    void test(List<String> list) {
        list.stream().forEach(s -> System.out.println(
          new StringBuilder().append(String.forma<caret>t("[%s]", s))
            .append("oops")
            .append("argh")
        ));
    }
}
