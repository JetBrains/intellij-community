// "Replace null check with orElse("")" "true"

import java.util.*;

public class Main {

  public Person work(Optional<String> o) {
    String v = o.orElse<caret>(null);
    return v == null? "" : v;
  }
}