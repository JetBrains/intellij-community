// "Sort content" "true-preview"

import java.util.*;

public class Main {
  private void test() {
    new String[] {
      "bbb", // b
    <caret>"aaa", // a
      "ccc" /* c */};
  }
}
