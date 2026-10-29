public class StringCompareToEqualityFix {

  boolean isSame(String name) {
    return <warning descr="'compareTo()' used for equality check; 'equals()' is clearer">name.<caret>compareTo("value") == 0</warning>;
  }
}
