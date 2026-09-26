/*
Value is always true (j >= 0; line#15)
  Left operand is >= 0 (j; line#15)
    'j' was assigned (=; line#14)
      Result of '+' is >= 0 (i + 1; line#14)
        Left operand is in {-1..Integer.MAX_VALUE-1} (i; line#14)
          'i' was assigned (=; line#13)
            Value is in {-1..Integer.MAX_VALUE-1} (s.indexOf(' '); line#13)
 */

class Test {
  private static void dosmth(String s) {
    int i = s.indexOf(' ');
    int j = i + 1;
    if (<selection>j >= 0</selection>) {

    }
  }
}