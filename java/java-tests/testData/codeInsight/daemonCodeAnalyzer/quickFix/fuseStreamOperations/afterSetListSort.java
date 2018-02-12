// "Fuse ArrayList, 'sort' and 'toArray' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public void testSetListSort(String[] args) {
      System.out.println(Arrays.stream(args).distinct().sorted().toArray());
  }
}