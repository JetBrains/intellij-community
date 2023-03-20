// "Fix all 'Redundant 'compare()' method call' problems in file" "true"
class Boxing {
  void test(Integer x, Integer y, int z) {
    boolean same = (int) x == y;
    boolean same1 = x > y;
    boolean same2 = (int) x != y;
    boolean same3 = x == z;
    boolean same4 = z == x;
  }
}