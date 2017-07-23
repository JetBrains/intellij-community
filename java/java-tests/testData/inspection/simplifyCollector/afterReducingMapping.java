// "Use 'Collectors.toMap' collector" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Test {
    public static void main(String[] args) {
        List<String> input = Arrays.asList("a", "bbb", "cc", "ddd", "  x", "ee");
        Map<Integer, String> map = input.stream().collect(Collectors.toMap(String::length, String::trim, (s1, s2) -> s1.compareTo(s2) > 0 ? s1 : s2));
        System.out.println(map);
    }
}