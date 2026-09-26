class Main {
  interface I{}
  enum ENNN  implements I{AAA}

  public static void test3(ENNN en) {
    switch (en) {
        case ENNN.AAA -> <caret>
    }
  }
}

