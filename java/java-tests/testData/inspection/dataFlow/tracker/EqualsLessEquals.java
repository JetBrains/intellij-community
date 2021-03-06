/*
Value is always false (i == expected; line#15)
  Condition 'i != expected' was checked before (i == expected; line#9)
 */
class A
  void compareInts(int i) {
    int expected = new Random().nextInt(100);
    int result;
    if (i == expected) {
      result = 0;
    }
    else if (i < expected) {
      result = -1;
    }
    else if (<selection>i == expected</selection>) {
      result = 0;
    }
  }
}