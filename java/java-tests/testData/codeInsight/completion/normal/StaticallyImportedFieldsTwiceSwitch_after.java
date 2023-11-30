import static C.AA.*;
class C {
  enum AA {Abc}

  public static void main(AA a) {
    switch (a) {
      //simple 'A' can be associated with enum AA, it is allowed started from Java 21
        case Abc -> <caret>

    }
  }
}