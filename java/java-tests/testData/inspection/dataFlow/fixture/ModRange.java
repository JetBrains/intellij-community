public class ModRange {
  void testMult(int x, int y, boolean b) {
    if (<warning descr="Condition 'x * 2 == y * 2 + 1' is always 'false'">x * 2 == y * 2 + 1</warning>) {}
    if (x * 3 == y * 3 + 1) {} // possible through overflow
    if (<warning descr="Condition 'x * 6 == y * 2 + 1' is always 'false'">x * 6 == y * 2 + 1</warning>) {}
    
    int z = x * 4 + (b ? 1 : 3);
    switch (z % 8) {
      case -1:break;
      case <warning descr="Switch label '-2' is unreachable">-2</warning>:break;
      case -3:break;
      case <warning descr="Switch label '-4' is unreachable">-4</warning>:break;
      case -5:break;
      case <warning descr="Switch label '-6' is unreachable">-6</warning>:break;
      case -7:break;
      case <warning descr="Switch label '0' is unreachable">0</warning>:break;
      case 1:if(<warning descr="Condition 'b' is always 'true'">b</warning>) {} break;
      case <warning descr="Switch label '2' is unreachable">2</warning>:break;
      case 3:if(<warning descr="Condition 'b' is always 'false'">b</warning>) {} break;
      case <warning descr="Switch label '4' is unreachable">4</warning>:break;
      case 5:break;
      case <warning descr="Switch label '6' is unreachable">6</warning>:break;
      case 7:break;
    }
  }

  void testMultLimited(int i, int j) {
    if (i >= 0 && i < 10 && j >= 0 && j < 10) {
      int sum = i * 3 + j * 3 + 1;
      if (<warning descr="Condition 'sum % 3 == 1' is always 'true'">sum % 3 == 1</warning>) {}
    }
  }

  void testShl(long x) {
    if (<warning descr="Condition '(x << 32) % 16 == 0' is always 'true'">(x << 32) % 16 == 0</warning>) {}
    if (<warning descr="Condition 'x << 32 == (x << 16) + 16' is always 'false'">x << 32 == (x << 16) + 16</warning>) {}
  }

  void testAnd(long x) {
    long res = x & 0xFFFF0001L;
    long rem = res % 16;
    if (<warning descr="Condition 'rem == 0 || rem == 1' is always 'true'">rem == 0 || <warning descr="Condition 'rem == 1' is always 'true' when reached">rem == 1</warning></warning>) {}
    res = x & 0xFFFF0001; // res can be negative now
    rem = res % 16;
    if (<warning descr="Condition 'rem == 0 || rem == 1 || rem == -15' is always 'true'">rem == 0 || rem == 1 || <warning descr="Condition 'rem == -15' is always 'true'">rem == -15</warning></warning>) {}
  }
  
  void testAnd2(long x) {
    long hi = x & 0xFF00;
    long lo = x & 0x00FF;
    if (hi == lo && <warning descr="Condition 'hi % 32 == 0' is always 'true' when reached">hi % 32 == 0</warning>) {}
  }
}
