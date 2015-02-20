import static C.A.*;
class C {
  enum A {Abc}

  public static void main(A a) {
    switch (a) {
        case Abc:<caret>

    }
  }
}