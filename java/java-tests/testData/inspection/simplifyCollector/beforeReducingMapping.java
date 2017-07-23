// "Use 'Collectors.toMap' collector" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Test {
    public static void main(String[] args) {
        List<String> input = Arrays.asList("a", "bbb", "cc", "ddd", "  x", "ee");
        Map<Integer, String> map = input.stream().collect(Collectors
                                                            .gr<caret>oupingBy(String::length, Collectors.mapping(String::trim, Collectors
                                                              .collectingAndThen(Collectors
                                                                                   .reducing((s1, s2) -> s1.compareTo(s2) > 0 ? s1 : s2), Optional::get))));
        System.out.println(map);
    }
}