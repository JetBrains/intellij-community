// "Use 'Collectors.toMap' collector" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Test {
    public static void main(String[] args) {
        List<String> input = Arrays.asList("a", "bbb", "cc", "ddd", "  x", "ee");
        // group by length
        // <= reduce to max
        /*need to unwrap optional*/
        Map<Integer, String> map2 = input.stream().collect(
                Collectors.toMap(String::length, Function.identity(), (s1, s2) -> s1.compareTo(s2) > 0 ? /*find max*/ s1 : s2));
        System.out.println(map2);
    }
}