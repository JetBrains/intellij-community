public class NanoTimeMagnitudeComparison {

  boolean deadlineLess(long deadline) {
    return <warning descr="'System.nanoTime()' value is compared by magnitude; compare the difference with zero instead">System.nanoTime() < deadline</warning>;
  }

  boolean deadlineGreater(long deadline) {
    return <warning descr="'System.nanoTime()' value is compared by magnitude; compare the difference with zero instead">deadline > System.nanoTime()</warning>;
  }

  boolean endLessEqual(long end) {
    return <warning descr="'System.nanoTime()' value is compared by magnitude; compare the difference with zero instead">System.nanoTime() <= end</warning>;
  }

  boolean startGreaterEqual(long start) {
    return <warning descr="'System.nanoTime()' value is compared by magnitude; compare the difference with zero instead">start >= System.nanoTime()</warning>;
  }

  boolean parenthesized(long x) {
    return <warning descr="'System.nanoTime()' value is compared by magnitude; compare the difference with zero instead">(System.nanoTime()) < x</warning>;
  }

  // The correct idiom subtracts first, so the operand is a subtraction and not a bare call: no warning.
  boolean difference(long start, long timeoutNanos) {
    return System.nanoTime() - start < timeoutNanos;
  }

  boolean differenceWithZero(long start) {
    return System.nanoTime() - start > 0;
  }

  // Equality is out of scope: no warning.
  boolean equality(long deadline) {
    return System.nanoTime() == deadline;
  }

  // A different clock method: no warning.
  boolean milliseconds(long deadline) {
    return System.currentTimeMillis() < deadline;
  }

  // Not System.nanoTime() at all: no warning.
  long nanoTime() {
    return 0;
  }

  boolean ownMethod(long x) {
    return nanoTime() < x;
  }
}
