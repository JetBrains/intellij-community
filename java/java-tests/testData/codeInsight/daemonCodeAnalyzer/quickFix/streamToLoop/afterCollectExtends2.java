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
      List<List<? extends CharSequence>> list = new ArrayList<>();
      for (CharSequence charSequence : getList()) {
          List<? extends CharSequence> charSequences = asList(charSequence);
          list.add(charSequences);
      }
      List<? extends List<? extends CharSequence>> res2 = list;
    System.out.println(res2);
  }
}