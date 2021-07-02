import java.util.*;

class Test {
  void testEnumRechable(Day d) {
    switch (d) {
      case MONDAY, TUESDAY:
        throw new IllegalArgumentException();
      default:
        break;
    }
    System.out.println();
  }

  void testEnumUncreachable1(Day d) {
    switch (d) {
      case MONDAY, TUESDAY, default:
        throw new IllegalArgumentException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void testEnumUnreachable2(Day d) {
    switch (d) {
      case (Day dd && true):
        throw new IllegalArgumentException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void testSealedClassReachable1(I i) {
    switch (i) {
      case First f:
        throw new IllegalArgumentException();
      case Second s:
        break;
    }
    System.out.println();
  }

  void testSealedClassReachable2(I i) {
    switch (i) {
      case First f:
        throw new IllegalArgumentException();
      case Second s:
        throw new IllegalArgumentException();
    }
    System.out.println();
  }

  void testSealedClassUnreachable1(I i) {
    switch (i) {
      case Object o:
        throw new IllegalArgumentException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}

sealed interface I {}
final class First implements I {}
final class Second implements I {}
