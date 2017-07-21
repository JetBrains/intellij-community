// "Replace with collect" "true"
import java.util.*;
import java.util.function.Function;

class Test {
  public static <T> List<TokenFilter<T>> fromString(final T src, Function<T, List<String>> extractor) {
    final List<TokenFilter<T>> result = new ArrayList<>();
    for (final String st : extrac<caret>tor.apply(src)) {
      result.add(new TokenFilter<T>(st));
    }
    return result;
  }
  
  static class TokenFilter<T> {
    public TokenFilter(String s) {
    }
  }
}