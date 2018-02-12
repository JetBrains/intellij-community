// "Fuse 'toArray' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public String[] testToArray(String[] args) {
    List<String> list = Arrays.stream(args).co<caret>llect(Collectors.toList());
    return list.toArray(new String[list.size()]);
  }
}