import java.util.*;

public class CompletionShouldNotAddExtraComma {
  List<String> getResult(String str) {
    if (str == null) return Collections.empty<caret>
    Integer a = 1;
    return Collections.emptyList();
  }
}
