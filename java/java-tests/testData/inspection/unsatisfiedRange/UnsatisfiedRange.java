import org.jetbrains.annotations.Range;

class Test {
  @Range(from = 0, to = Integer.MAX_VALUE) int getValue(int x) {
    if (Math.random() > 0.5) return <warning descr="Return value '-1' is outside of declared range '>= 0'">-1</warning>;
    if (Math.random() > 0.5 && x < 0) return <warning descr="Return value range '<= -1' is outside of declared range '>= 0'">x</warning>;
    return x;
  }

  @Range(from = 0, to = Integer.MAX_VALUE)
  int ternary(int x) {
    return x >= 0 ? x : x == -1 ? 1 : <warning descr="Return value range 'in {-1073741824..-1}' is outside of declared range '>= 0'">x/2</warning>;
  }
}