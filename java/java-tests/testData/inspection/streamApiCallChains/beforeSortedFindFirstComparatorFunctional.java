// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

// IDEA-188598
public class SimplifyStream {
  public void foo() {
    final Optional<Date> first = getDates()
      .stream()
      .sorted(Date::compareTo)
      .fi<caret>ndFirst();

    final Optional<Date> first2 = getDates()
      .stream()
      .sorted(Comparator.naturalOrder())
      .findFirst();
  }
  public List<Date> getDates() {
    return new ArrayList<>();
  }
}