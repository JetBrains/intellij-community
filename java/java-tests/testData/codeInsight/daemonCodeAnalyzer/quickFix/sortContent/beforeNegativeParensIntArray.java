// "Sort content" "true"

import java.util.*;

public class Main {
  private void test() {
    new int[] {1, <caret>4, 3, -(-12), -1, ((2))};
  }
}
