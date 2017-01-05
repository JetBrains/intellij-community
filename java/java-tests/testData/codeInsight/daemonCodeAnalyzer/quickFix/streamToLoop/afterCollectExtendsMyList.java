// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  static class MyList<T> extends ArrayList<List<? extends CharSequence>> {
  }

  private List<? extends CharSequence> asList(CharSequence s) {
    return Collections.singletonList(s);
  }

  public List<? extends CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
      MyList<? extends List<? extends CharSequence>> res2 =
              new MyList<>();
      for (CharSequence charSequence : getList()) {
          List<? extends CharSequence> charSequences = asList(charSequence);
          res2.add(charSequences);
      }
      System.out.println(res2);
  }
}