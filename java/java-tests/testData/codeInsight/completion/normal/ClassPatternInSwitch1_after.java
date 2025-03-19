class Main {
  sealed interface I{}
  enum ENNNNN  implements I{AAA}
  final class AAA implements I{}

  public static void test3(I en) {
    switch (en) {
        case ENNNNN<caret>
    }
  }
}
