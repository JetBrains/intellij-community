// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public List<? extends CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
      List<CharSequence> list = new ArrayList<>();
      for (CharSequence charSequence : getList()) {
          if (charSequence != null) {
              list.add(charSequence);
          }
      }
      List<? extends CharSequence> res = list;
    System.out.println(res);
  }
}
