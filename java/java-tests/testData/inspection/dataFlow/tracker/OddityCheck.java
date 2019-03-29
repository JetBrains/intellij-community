/*
Value is always false (x * 2 == y * 2 + 1)
  Range of '*' result is {Integer.MIN_VALUE..Integer.MAX_VALUE-1}: even (x * 2)
  Range of '+' result is {-2147483647..Integer.MAX_VALUE}: odd (y * 2 + 1)
    Range of '*' result is {Integer.MIN_VALUE..Integer.MAX_VALUE-1}: even (y * 2)
 */
class Test {
  void test(int x, int y) {
    // May produce better result in future (keep only mod info (even/odd), remove range which is irrelevant here)
    if (<selection>x * 2 == y * 2 + 1</selection>) {

    }
  }
}