// "Fuse 'toArray' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public String[] testToArray(String[] args) {
    Set<String> set = Arrays.stream(args).co<caret>llect(Collectors.toCollection(TreeSet::new));
    return set.toArray(new String[set.size()]);
  }
}