import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class OptionalStreamInlining {
  public static void testOptionalStream(String s) {
    Optional<List<String>> strings = Optional.ofNullable(s)
      .map(OptionalStreamInlining::getLetters);
    Optional<String> s1 = strings
      .stream()
      .flatMap(Collection::stream)
      .min(Comparator.naturalOrder());
    if (s1.isPresent()) {
      System.out.println(s.length());
    } else {
      System.out.println();
      System.out.println(s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
    }
  }
  public static void testOptionalStream2(String s) {
    Optional<String> s1 = Optional.ofNullable(s)
      .map(OptionalStreamInlining::getLetters)
      .stream()
      .flatMap(Collection::stream)
      .min(Comparator.naturalOrder());
    if (s1.isPresent()) {
      System.out.println(s.length());
    } else {
      System.out.println();
      System.out.println(s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
    }
  }
  private static List<String> getLetters(String t){
    ArrayList<String> strings = new ArrayList<>();
    strings.add("1");
    return strings;
  }
}
