import java.util.*;

public class FloatingPointRanges {
  public static boolean isSpecial(double value) {
    return value == 1.0 || value == -1.0 || value == 0.0;
  }

  void testSimple(double d) {
    if (<warning descr="Condition 'd > Double.POSITIVE_INFINITY' is always 'false'">d > Double.POSITIVE_INFINITY</warning>) {}
    if (<warning descr="Condition 'd < Double.NEGATIVE_INFINITY' is always 'false'">d < Double.NEGATIVE_INFINITY</warning>) {}
    if (d > 1) {
      if (<warning descr="Condition 'd > 1' is always 'true'">d > 1</warning>) {}
      if (d > 2) {}
      if (<warning descr="Condition 'd > 0' is always 'true'">d > 0</warning>) {}
      if (<warning descr="Condition 'd < 1' is always 'false'">d < 1</warning>) {}
      if (d < 2) {}
      if (<warning descr="Condition 'd < 0' is always 'false'">d < 0</warning>) {}
      if (<warning descr="Condition 'd == d' is always 'true'">d == d</warning>) {}
    } else {
      if (<warning descr="Condition 'd > 2' is always 'false'">d > 2</warning>) {}
      if (d > 0) {}
      if (d == d) {
        if (d == 0) {}
      }
    }
  }
  
  void testCompareToNaN(double d) {
    double d1 = Double.NaN;
    if (<warning descr="Condition 'd == d1' is always 'false'">d == d1</warning>) {}
    // suppressed: reported by another inspection
    if (d == Double.NaN) {}
  }

  void testSimpleFloat(float d) {
    if (<warning descr="Condition 'd > Float.POSITIVE_INFINITY' is always 'false'">d > Float.POSITIVE_INFINITY</warning>) {}
    if (<warning descr="Condition 'd < Float.NEGATIVE_INFINITY' is always 'false'">d < Float.NEGATIVE_INFINITY</warning>) {}
    if (d > 1) {
      if (<warning descr="Condition 'd > 1' is always 'true'">d > 1</warning>) {}
      if (d > 2) {}
      if (<warning descr="Condition 'd > 0' is always 'true'">d > 0</warning>) {}
      if (<warning descr="Condition 'd < 1' is always 'false'">d < 1</warning>) {}
      if (d < 2) {}
      if (<warning descr="Condition 'd < 0' is always 'false'">d < 0</warning>) {}
      if (<warning descr="Condition 'd == d' is always 'true'">d == d</warning>) {}
    } else {
      if (<warning descr="Condition 'd > 2' is always 'false'">d > 2</warning>) {}
      if (d > 0) {}
      if (d == d) {
        if (d == 0) {}
      }
    }
  }
}