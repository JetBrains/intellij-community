// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {
  public static <T> List<TokenFilter<T>> fromString(final T src, Function<T, List<String>> extractor) {
    final List<TokenFilter<T>> result = extractor.apply(src).stream().map(st -> new TokenFilter<T>(st)).collect(Collectors.toList());
      return result;
  }
  
  static class TokenFilter<T> {
    public TokenFilter(String s) {
    }
  }
}