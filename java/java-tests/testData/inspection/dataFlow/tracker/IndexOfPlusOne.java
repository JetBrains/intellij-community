/*
Value is always true (j >= 0)
  Left operand is >= 0 (j)
    'j' was assigned (i + 1)
      Result of '+' is >= 0 (i + 1)
        Left operand is in {-1..Integer.MAX_VALUE-1} (i)
          'i' was assigned (s.indexOf(' '))
            Value is in {-1..Integer.MAX_VALUE-1} (s.indexOf(' '))
 */

class Test {
  private static void dosmth(String s) {
    int i = s.indexOf(' ');
    int j = i + 1;
    if (<selection>j >= 0</selection>) {

    }
  }
}