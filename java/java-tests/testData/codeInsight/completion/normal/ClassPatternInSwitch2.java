class Main {
  interface I{}
  enum ENNNNN  implements I{AAA}
  final class AAAAAA implements I{}

  public static void test3(I en) {
    switch (en) {
      case AAAA<caret>
    }
  }
}
