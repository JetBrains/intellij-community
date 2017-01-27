// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static String test0(List<CharSequence> list) {
    return list.stream().skip(1).distinct().c<caret>ollect(Collectors.joining());
  }

  private static String test1(List<CharSequence> list, String delimiter) {
    return list.stream().skip(1).distinct().skip(2).collect(Collectors.joining(delimiter));
  }

  private static String test3(List<CharSequence> list, String delimiter) {
    return list.stream().skip(1).distinct().collect(Collectors.joining(delimiter, "<", ">"));
  }

  public static void main(String[] args) {
    System.out.println(test0(Arrays.asList("a", "b", "e", "c", "d", "e", "a")));
    System.out.println(test1(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
    System.out.println(test3(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
  }
}