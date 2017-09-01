// "Replace null check with orElse("")" "true"

import java.util.*;

public class Main {

  public Person work(Optional<String> o) {
      return o.orElse("");
  }
}