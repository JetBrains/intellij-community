import java.util.*;

class LessThanRelations {
  void foo1(long f1, long f2, long t1, long t2) {
    if (t1 < f2 || f1 > f2) return;
    if (f1 <= f2 && t1 >= t2) return;
    if (f1 > f2 && t1 < t2) return;
    if (f1 <= f2) return;
    if (t1 >= t2) return; // TODO: must be always true
  }

  void foo2(long f1, long f2, long t1, long t2) {
    if (f1 <= f2 && t1 >= t2) return;
    if (f1 > f2 && t1 < t2) return;
    if (f1 <= f2) return;
    if (<warning descr="Condition 't1 >= t2' is always 'true'">t1 >= t2</warning>) return;
  }

  void foo3(long f1, long f2, long t1, long t2) {
    if (t1 < f2 || f1 > f2) return;
    if (f1 > f2 && t1 < t2) return;
    if (f1 <= f2) return;
    if (t1 >= t2) return; // TODO: must be always true
  }

  void foo4(long f1, long f2, long t1, long t2) {
    if (f1 > f2 && t1 < t2) return;
    if (f1 <= f2) return;
    if (<warning descr="Condition 't1 >= t2' is always 'true'">t1 >= t2</warning>) return;
  }

  // IDEA-184278
  void m(int value) {
    for (int i = 0; i < value; i++) {
      if (<warning descr="Condition 'i < value' is always 'true'">i < value</warning>) System.out.println();
    }
  }

  void transitive(int a, int b, int c) {
    if(a > b && b >= c) {
      System.out.println("possible");
      if(<warning descr="Condition 'c == a' is always 'false'">c == a</warning>) {
        System.out.println("Impossible");
      }
      if(<warning descr="Condition 'c > a' is always 'false'">c > a</warning>) {
        System.out.println("Impossible");
      }
    }
  }

  void aioobe(Object[] arr, int pos) {
    if(pos >= arr.length) {
      System.out.println(arr[<warning descr="Array index is out of bounds">pos</warning>]);
    }
  }

  void wrongOrder(Object[] arr, Object e) {
    int index = 0;
    while(arr[index] != e && <warning descr="Condition 'index < arr.length' is always 'true' when reached">index < arr.length</warning>) {
      index++;
    }
    System.out.println(index);
  }

  void list(List<String> list, int index) {
    if(index >= list.size()) {
      System.out.println("Big index");
    } else if(index > 0 && <warning descr="Condition '!list.isEmpty()' is always 'true' when reached">!<warning descr="Result of 'list.isEmpty()' is always 'false'">list.isEmpty()</warning></warning>) {
      System.out.println("ok");
    }
  }
}
final class Range {
  final long myFrom; // inclusive
  final long myTo; // inclusive

  public Object smth(Object other) {
    if (other instanceof Range) {
      long from = ((Range)other).myFrom;
      long to = ((Range)other).myTo;
      if (to < myFrom || from > myTo) return this;
      if (from <= myFrom && to >= myTo) return new Object();
      if (from > myFrom && to < myTo) {
        return new RangeSet(new long[]{myFrom, from - 1, to + 1, myTo});
      }
      if (from <= myFrom) {
        return new Range(to + 1, myTo);
      }
      if (to >= myTo) {
        return new Range(myFrom, from - 1);
      }
      throw new RuntimeException("Impossible: " + this + ":" + other);
    }
    return this;
  }

  Range(long from, long to) {
    myFrom = from;
    myTo = to;
  }
}

final class RangeSet {
  RangeSet(long[] arr) {}
}
