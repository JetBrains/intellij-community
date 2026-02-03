class Main {
  sealed interface I{}
  enum EN  implements I{AAA}

  public static void test3(I en) {
    switch (en) {
      case EN.A<caret>
    }
  }
}
