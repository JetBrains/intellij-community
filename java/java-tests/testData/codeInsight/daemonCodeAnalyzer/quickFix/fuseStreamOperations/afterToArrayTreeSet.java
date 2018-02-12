// "Fuse 'toArray' into the Stream API chain" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public String[] testToArray(String[] args) {
      return Arrays.stream(args).distinct().sorted().toArray(String[]::new);
  }
}