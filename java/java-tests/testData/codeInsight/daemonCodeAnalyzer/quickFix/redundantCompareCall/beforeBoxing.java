// "Fix all 'Redundant 'compare()' method call' problems in file" "true"
class Boxing {
  void test(Integer x, Integer y, int z) {
    boolean same = Integer.<caret>compare(x, y) == 0;
    boolean same1 = Integer.compare(x, y) > 0;
    boolean same2 = Integer.compare(x, y) != 0;
    boolean same3 = Integer.compare(x, z) == 0;
    boolean same4 = Integer.compare(z, x) == 0;
  }
}