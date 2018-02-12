// "Fuse HashSet and ArrayList into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Collection<String> test(String[] args) {
      return Arrays.stream(args).filter(String::isEmpty).distinct().collect(Collectors.toList());
  }
}