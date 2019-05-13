// "Fix all 'Redundant 'compare' method call' problems in file" "true"
class CompareTest {
  int testInteger(int a, int b) {
    if(a < b) return 1;
    if(a > b) return 2;
    if(a >= b) return 3;
    if(1 <= Integer.compare(a, b)) return 4;
    if(a != b) return 5;
    return 0;
  }

  int testShort(short a, byte b) {
    if(a < b) return 1;
    if(a >= b) return 2;
    if(1 <= Short.compare(a, b)) return 4;
    return 0;
  }

  int testByte(int a, int b) {
    if((byte) a < (byte) b) return 1;
    if(Byte.compare((byte)a, (byte)b) >= 1) return 2;
    return 0;
  }

  int testLong(long a, long b, long c, int d) {
    if(a + b < c + d) return 1;
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      /*8*/
      /*9*/
      /*10*/
      /*11*/
      /*12*/
      if(/*1*/(d /*6*/ > 0 ? /*7*/a : b) < c/*13*/) return 1;
    return 0;
  }
}