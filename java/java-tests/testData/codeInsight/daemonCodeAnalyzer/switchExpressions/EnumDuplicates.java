public class EnumDuplicates {

  sealed interface IN {}
  enum T1 implements IN { A, B, C; }
  enum T2 implements IN { A, B, C, D; }
  int testDuplicates1(IN i) {
    return switch (i) {
      case T1.A -> 1;
      case T1.B -> 2;
      case T1.C -> 3;
      case T2.A-> 4;
      case T2.B -> 4;
      case T2.C -> 5;
      default -> 6;
    };
  }

  int testDuplicates2(T1 e) {
    return switch(e) {
      case <error descr="Duplicate label 'A'">T1.A</error> -> 1;
      case <error descr="Duplicate label 'A'">A</error> -> 1;
      case T1.B -> 2;
      case T1.C -> 3;
    };
  }
  int testDuplicates3(T1 e) {
    return switch(e) {
      case A -> 1;
      case T1.B -> 2;
      case T1.C -> 3;
    };
  }

  int testDuplicates4(IN e) {
    return switch(e) {
      case <error descr="Cannot resolve symbol 'A'">A</error> -> 1;
      case T1.B -> 2;
      case T1.C -> 3;
    };
  }

  int testDuplicates5(T1 e) {
    return switch (e) {
      case <error descr="Duplicate label 'A'">A</error> -> 1;
      case <error descr="Duplicate label 'A'">A</error> -> 1;
      case B -> 2;
      case C -> 3;
    };
  }

  int testDuplicates6(T1 e) {
    return switch (e) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };
  }
}