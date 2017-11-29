// "Fuse 'sort' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public List<String> testToArray(String[] args) {
    List<String> list = Arrays.stream(args).c<caret>ollect(Collectors.toList());
    list.sort(null);
    return list;
  }
}