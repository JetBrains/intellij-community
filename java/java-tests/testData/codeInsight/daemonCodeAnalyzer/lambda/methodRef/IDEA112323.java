import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test1
{
    public static void main(Stream<Map.Entry<String, Long>> stream)
    {
        Stream<String> map = stream.map(Map.Entry::getKey);
    }

    public static void main(String[] args)
    {
        Map<String, Long> storage = new HashMap<>();
        storage.put("One", 1l);
        List<String> keys = storage
                .entrySet()
                .stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        keys.stream().forEach(System.out::println);
    }
}
