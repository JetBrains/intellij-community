// "Replace Stream API chain with loop" "true"

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public List<? extends CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
    List<? extends CharSequence> res = getList().stream().filter(Objects::nonNull).col<caret>lect(Collectors.toList());
    System.out.println(res);
  }
}
