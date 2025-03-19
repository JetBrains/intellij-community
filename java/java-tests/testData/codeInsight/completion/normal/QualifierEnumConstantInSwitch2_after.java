class Main {
  sealed interface I{}
  enum EN  implements I{AAA}

  public static void test3(EN en) {
    switch (en) {
        case EN.AAA -> <caret>
    }
  }
}
