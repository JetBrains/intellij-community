// "Fuse 'toArray' into the Stream API chain" "true-preview"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public String[] testToArray(String[] args) {
    Set<String> set = Arrays.stream(args).co<caret>llect(Collectors.toCollection(TreeSet::new));
    return set.toArray(new String[set.size()]);
  }
}