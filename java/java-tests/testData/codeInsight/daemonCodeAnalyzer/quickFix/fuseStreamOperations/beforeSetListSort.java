// "Fuse ArrayList, 'sort' and 'toArray' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public void testSetListSort(String[] args) {
    Set<String> set = Arrays.stream(args).co<caret>llect(Collectors.toSet());
    List<String> list = new ArrayList<>(set); // foo
    list.sort(null); // bar
    System.out.println(list.toArray(/*baz*/));
  }
}