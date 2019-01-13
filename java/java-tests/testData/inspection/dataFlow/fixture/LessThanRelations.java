import java.util.*;

class LessThanRelations {
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
