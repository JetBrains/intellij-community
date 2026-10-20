package foo.copy.past;


import foo.copy.past.Something.ArrayList;
import static foo.copy.past.Something.valueOf;
import static java.lang.String.format;
import static java.util.List.of;

import java.util.HashSet;
import java.util.List;

public class To {
  public static void main(String[] args) {
    ArrayList<String> a = new ArrayList<>();
    new HashSet<>();
    format("1");
    valueOf(1);


      List<String> a2 = new java.util.ArrayList<>();
      new HashSet<>(a2);

      of("a", "b", "c");
      format("1");
      String.valueOf(1);
<caret>
  }
}
