// "Fuse HashSet and ArrayList into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Collection<String> test(String[] args) {
    List<String> list = Arrays.stream(args).filter(String::isEmpty).coll<caret>ect(Collectors.toList());
    HashSet<String> strings = new HashSet<>(list);
    return new ArrayList<>(strings);
  }
}