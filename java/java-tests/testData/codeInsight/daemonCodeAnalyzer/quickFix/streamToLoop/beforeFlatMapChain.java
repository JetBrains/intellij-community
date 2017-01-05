// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  private static long test(List<? extends String> list) {
    return Stream.of(0, null, "1", list).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).coun<caret>t();
  }

  public static void main(String[] args) {
    test(Arrays.asList("aa", "bbb", "c", null, "dd"));
  }
}