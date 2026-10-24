public class StringCompareToEquality {

  boolean eq(String a, String b) {
    return <warning descr="'compareTo()' used for equality check; 'equals()' is clearer">a.compareTo(b) == 0</warning>;
  }

  boolean ne(String a, String b) {
    return <warning descr="'compareTo()' used for equality check; 'equals()' is clearer">a.compareTo(b) != 0</warning>;
  }

  boolean zeroOnLeft(String a, String b) {
    return <warning descr="'compareTo()' used for equality check; 'equals()' is clearer">0 == a.compareTo(b)</warning>;
  }

  boolean literalArgument(String name) {
    return <warning descr="'compareTo()' used for equality check; 'equals()' is clearer">name.compareTo("value") == 0</warning>;
  }

  // No warning: an ordering comparison, not an equality check.
  boolean ordering(String a, String b) {
    return a.compareTo(b) > 0;
  }

  // No warning: compared with a value other than zero.
  boolean notZero(String a, String b) {
    return a.compareTo(b) == 1;
  }

  // No warning: a nullable argument would change behavior (compareTo(null) throws, equals(null) returns false).
  boolean nullableArgument(String a) {
    return a.compareTo(null) == 0;
  }
}
