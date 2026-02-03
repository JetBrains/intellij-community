/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
abstract class AbstractClass1 implements Comparable<AbstractClass1> {

  int field = 1;

  public int compareTo(AbstractClass1 other) {
    return field > other.field ? 1 : (field == other.field ? 0 : -1);
  }
}