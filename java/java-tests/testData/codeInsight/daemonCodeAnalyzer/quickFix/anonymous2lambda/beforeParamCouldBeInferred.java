// "Replace with lambda" "true-preview"

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  private static final Map<String, Mood> WORD_TO_MOOD = null;

  public static void analyseMood(String[] wordsInMessage) {
    Stream.of(wordsInMessage)
      .map(new Func<caret>tion<String, Mood>() {
        @Override
        public Mood apply(String s) {
          return WORD_TO_MOOD.get(s);
        }
      })
      .filter(mood -> mood != null)
      .collect(Collectors.toSet());
  }

  enum Mood {}
}