// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.util.Optional;

public class Main {
  public void test(Optional<String> opt) {
    if(opt.isPres<caret>ent()) {
      if(opt.get().equals("abc"))
        System.out.println(opt.get());
    }
  }
}
