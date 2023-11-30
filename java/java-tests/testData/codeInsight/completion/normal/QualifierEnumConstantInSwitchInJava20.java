class Main {
  interface I{}
  enum ENNN  implements I{AAA}

  public static void test3(I en) {
    switch (en) {
      case ENNN.AA<caret>
    }
  }
}

