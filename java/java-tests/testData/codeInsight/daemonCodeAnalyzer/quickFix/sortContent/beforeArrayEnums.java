// "Sort content" "true-preview"

import java.util.*;

public class Main {
  enum E {A,B,C,D}

  private void test() {
    new E[] {E.B, E<caret>.A, E.C, E.D, E.A};
  }
}
