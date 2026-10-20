package foo.copy.past;


import foo.copy.past.Something.ArrayList;
import static foo.copy.past.Something.valueOf;
import static java.lang.String.format;

import java.util.HashSet;

public class To {
  public static void main(String[] args) {
    ArrayList<String> a = new ArrayList<>();
    new HashSet<>();
    format("1");
    valueOf(1);

    <caret>
  }
}
