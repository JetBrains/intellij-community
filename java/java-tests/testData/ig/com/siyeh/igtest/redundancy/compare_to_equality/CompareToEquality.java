public class CompareToEquality {

  boolean eq(String a, String b) {
    return a.<warning descr="'compareTo()' used for equality check">compareTo</warning>(b) == 0;
  }

  boolean ne(String a, String b) {
    return a.<warning descr="'compareTo()' used for equality check">compareTo</warning>(b) != 0;
  }

  boolean zeroOnLeft(String a, String b) {
    return 0 == a.<warning descr="'compareTo()' used for equality check">compareTo</warning>(b);
  }

  boolean literalArgument(String name) {
    return name.<warning descr="'compareTo()' used for equality check">compareTo</warning>("value") == 0;
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
