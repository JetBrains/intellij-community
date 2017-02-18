// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public List<? extends CharSequence> asList(CharSequence s) {
    return Collections.singletonList(s);
  }

  public List<? extends CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
    Map<? extends CharSequence, ? extends List<? extends CharSequence>> map = getList()
      .stream().filter(Objects::nonNull).co<caret>llect(Collectors.toMap(Function.identity(), this::asList));
    System.out.println(map);
  }
}
