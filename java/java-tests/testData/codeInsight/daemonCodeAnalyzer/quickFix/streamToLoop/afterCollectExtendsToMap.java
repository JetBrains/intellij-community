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
      Map<CharSequence, List<? extends CharSequence>> result = new HashMap<>();
      for (CharSequence charSequence : getList()) {
          if (Objects.nonNull(charSequence)) {
              if (result.put(charSequence, asList(charSequence)) != null) {
                  throw new IllegalStateException("Duplicate key");
              }
          }
      }
      Map<? extends CharSequence, ? extends List<? extends CharSequence>> map = result;
    System.out.println(map);
  }
}
