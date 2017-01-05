// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private List<? extends CharSequence> asList(CharSequence s) {
    return Collections.singletonList(s);
  }

  public List<? extends CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
    List<? extends List<? extends CharSequence>> res2 = getList().stream().map(this::asList).col<caret>lect(Collectors.toList());
    System.out.println(res2);
  }
}