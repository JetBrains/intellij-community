import java.util.*;

public class BackPropagation {
  void testOverflowDetection(int[] arr1, int[] arr2, int offset) {
    int l1 = arr1.length;
    int l2 = arr2.length;
    if (l1 < l2 || offset < 0) return;
    if (l1 - offset >= l2) {
      if (l1 == l2 && <warning descr="Condition 'offset == 0' is always 'true' when reached">offset == 0</warning>) {}
    }
    if (l1 - offset <= l2) {
      if (l1 == l2 && offset == 0) {}
    }
  }

  void test(int x, int y) {
    if (x + 1 > 0) {
      if (<warning descr="Condition 'x == -2' is always 'false'">x == -2</warning>) {
      }
      if (<warning descr="Condition 'x == -1' is always 'false'">x == -1</warning>) {
      }
      if (x == 0) {
      }
      if (x == Integer.MAX_VALUE - 1) {
      }
      if (<warning descr="Condition 'x == Integer.MAX_VALUE' is always 'false'">x == Integer.MAX_VALUE</warning>) {
      }

      if (x - 1 > y) {
        if (<warning descr="Condition 'x > y' is always 'true'">x > y</warning>) {
        }
      }
    }
    if (<warning descr="Condition 'x + 1 == y && x == y' is always 'false'">x + 1 == y && <warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning></warning>) {
    }
    if (<warning descr="Condition 'x + 1 + 2 + 3 == y && x == y' is always 'false'">x + 1 + 2 + 3 == y && <warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning></warning>) {
    }
  }

  void test(int x, int y, int z, int t) {
    if (x == z) {
      if (x + y - z == t && <warning descr="Condition 'y == t' is always 'true' when reached">y == t</warning>) {}
    }
  }

  public final void read(char buffer[], int startOffset, int length) {
    if (startOffset < 0
        || startOffset > buffer.length
        || length < 0
        || startOffset + length > buffer.length
        || startOffset + length < 0) {
      throw new IndexOutOfBoundsException();
    }
    // This is always false, but we cannot reliably detect this yet. If startOffset == buffer.length
    // then startOffset + length <= buffer.length implies that length == 0 *or* startOffset + length overflows
    // The latest case is ruled out by startOffset + length < 0, but our memory state is unable to track this. 
    if (startOffset == buffer.length && length > 0) {
    }
  }

  void arr(int idx, int[] arr1, int[] arr2) {
    arr1 = new int[6];
    arr2 = new int[6];
    arr1[idx] = 0;
    arr1[idx+3] = 0;
    arr2[idx] = 0;
    arr2[idx+3] = 0;
    arr2[<warning descr="Array index is out of bounds">idx-3</warning>] = 0;
  }

  void update(int x, int y, int z) {
    if (y == z) {
      <warning descr="Variable update does nothing">x</warning>+=y-z;
      <warning descr="Variable update does nothing">x</warning>-=y-z;
    }
  }
  
  void test2(String fqName) {
    int spaceIdx = fqName.indexOf(' ');
    int lastDotIdx = fqName.lastIndexOf('.');

    int parenIndex = fqName.indexOf('(');

    while (lastDotIdx > parenIndex) lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);

    boolean notype = false;
    if (spaceIdx < 0 || spaceIdx + 1 > lastDotIdx || <warning descr="Condition 'spaceIdx > parenIndex' is always 'false'">spaceIdx > parenIndex</warning>) {
      notype = true;
    }
  }
  
  void test3(int c, String text) {
    if (c < 1 || c > text.length() - 1) return;
    if (<warning descr="Condition 'text.length() > c' is always 'true'">text.length() > c</warning>) {}
  }

  void testDiff(int x, int y) {
    if (<warning descr="Condition 'x - y > 0 && x == y' is always 'false'">x - y > 0 && <warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning></warning>) {}
    if (y > 0 && x >= y) {
      if (<warning descr="Condition 'x - y < 0' is always 'false'">x - y < 0</warning>) {}
      if (<warning descr="Condition 'x - y > 0 && x == y' is always 'false'">x - y > 0 && <warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning></warning>) {}
    }
    if (x > 0 && y > 0) {
      if (<warning descr="Condition 'x - y > 1 && x <= y' is always 'false'">x - y > 1 && <warning descr="Condition 'x <= y' is always 'false' when reached">x <= y</warning></warning>) {}
      if (x - y > -1 && x <= y) {}
      if (x - y < -1 && <warning descr="Condition 'x <= y' is always 'true' when reached">x <= y</warning>) {}
      if (<warning descr="Condition 'x - y == y && x == y' is always 'false'">x - y == y && <warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning></warning>) {}
      if (x - y < y && (x == y || x < y)) {}
      if (x - y > y && (<warning descr="Condition 'x == y || x > y' is always 'true' when reached"><warning descr="Condition 'x == y' is always 'false' when reached">x == y</warning> || <warning descr="Condition 'x > y' is always 'true' when reached">x > y</warning></warning>)) {}
    } else {
      if (x - y > 1 && x <= y) {}
      if (x - y > -1 && x <= y) {}
      if (x - y < -1 && x <= y) {}
      if (x - y == y && x == y) {}
      if (x - y < y && (x == y || x < y)) {}
    }
  }

  void mixedTypes(int len, long len2, int max) {
    if (len > 0 && len + len2 > max) {
      System.out.println();
    }
  }
}
