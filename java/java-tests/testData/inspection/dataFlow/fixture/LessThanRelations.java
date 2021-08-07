import java.util.*;

class LessThanRelations {
  void testNE(int a, int b) {
    if (a != b) {
      if (<warning descr="Condition 'a == b' is always 'false'">a == b</warning>) {}
      if (a > b) {}
      if (a < b) {}
    }
  }
  
  void test(int a, int b, int c, int d) {
    if ((a >= c && b <= d) ||
        (b >= c && a <= d)) {
      if (<warning descr="Condition 'b > d && a > d' is always 'false'">b > d && <warning descr="Condition 'a > d' is always 'false' when reached">a > d</warning></warning>) {}
    }
    if (a > b) {
      if (c < d) {
        if (<warning descr="Condition 'a == c && b == d' is always 'false'">a == c && <warning descr="Condition 'b == d' is always 'false' when reached">b == d</warning></warning>) {

        }
      }
    }
  }

  void foo1(long f1, long f2, long t1, long t2) {
    if (t1 < f2 || f1 > f2) return;
    if (<warning descr="Condition 'f1 <= f2' is always 'true'">f1 <= f2</warning> && t1 >= t2) return;
    if (<warning descr="Condition 'f1 > f2 && t1 < t2' is always 'false'"><warning descr="Condition 'f1 > f2' is always 'false'">f1 > f2</warning> && t1 < t2</warning>) return;
    if (<warning descr="Condition 'f1 <= f2' is always 'true'">f1 <= f2</warning>) return;
    if (t1 >= t2) return;
  }

  void foo2(long f1, long f2, long t1, long t2) {
    if (f1 <= f2 && t1 >= t2) return;
    if (f1 > f2 && t1 < t2) return;
    if (f1 <= f2) return;
    if (<warning descr="Condition 't1 >= t2' is always 'true'">t1 >= t2</warning>) return;
  }

  void foo3(long f1, long f2, long t1, long t2) {
    if (t1 < f2 || f1 > f2) return;
    if (<warning descr="Condition 'f1 > f2 && t1 < t2' is always 'false'"><warning descr="Condition 'f1 > f2' is always 'false'">f1 > f2</warning> && t1 < t2</warning>) return;
    if (<warning descr="Condition 'f1 <= f2' is always 'true'">f1 <= f2</warning>) return;
    if (t1 >= t2) return;
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

  int test2(int a, int b, int c, int d) {
    assert a <= b;
    assert c <= d;

    int r = 1;
    if (b < c) {
      r = 2;
    } else if (d < a) {
      r = 3;
    } else if (b == c || a == d) {
      if (a < d) {
        r = 4;
      } else if (b > c) {
        r = 5;
      } else {
        r = 6;
      }
    }
    return r;
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
      if (<warning descr="Condition 'to >= myTo' is always 'true'">to >= myTo</warning>) {
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
