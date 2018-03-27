import org.jetbrains.annotations.*;
import java.util.*;

public class PrimitiveGetters {
  interface Xyz {
    boolean isFoo();
  }

  boolean test(Object[] locals, Object[] remotes) {
    for (int j = 0; j < locals.length; j++) {
      Object local = locals[j];
      if (local instanceof Xyz && remotes[j] instanceof Xyz) {
        Xyz localXyz = (Xyz)local;
        Xyz remoteXyz = (Xyz)local;
        if (<warning descr="Condition 'localXyz.isFoo() != remoteXyz.isFoo()' is always 'false'">localXyz.isFoo() != remoteXyz.isFoo()</warning>) {
          return false;
        }
      }
      else {
        return false;
      }
    }
    return true;
  }
}

// IDEA-146061
class SuggestionListFail {
  class Suggestion {

  }

  final protected @NotNull ArrayList<Suggestion> suggestions = new ArrayList<Suggestion>();
  final protected @NotNull HashSet<String> suggestionSet = new HashSet<String>();

  public SuggestionListFail() {
  }

  @Contract(pure = true)
  public boolean isEmpty() {
    return suggestions.isEmpty();
  }

  @NotNull
  public SuggestionListFail wrap(@Nullable SuggestionListFail prefixes, @Nullable SuggestionListFail suffixes) {
    SuggestionListFail wrappedList = new SuggestionListFail();

    if ((prefixes == null || prefixes.isEmpty()) && (suffixes == null || suffixes.isEmpty())) {
    } else if (prefixes == null || prefixes.isEmpty()) {
      for (Suggestion suffix : suffixes.suggestions) {
        for (Suggestion suggestion : suggestions) {
        }
      }
    } else if (suffixes == null || suffixes.isEmpty()) {
      for (Suggestion prefix : prefixes.suggestions) {
        for (Suggestion suggestion : suggestions) {
        }
      }
    } else {
      for (Suggestion prefix : prefixes.suggestions) {
        for (Suggestion suffix : suffixes.suggestions) {
          for (Suggestion suggestion : suggestions) {
          }
        }
      }
    }
    return wrappedList;
  }
}