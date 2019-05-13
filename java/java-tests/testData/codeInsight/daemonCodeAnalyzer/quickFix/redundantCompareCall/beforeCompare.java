// "Fix all 'Redundant 'compare' method call' problems in file" "true"
class CompareTest {
  int testInteger(int a, int b) {
    if(Integer.c<caret>ompare(a, b) < 0) return 1;
    if(Integer.compare(a, b) > 0) return 2;
    if(0 <= Integer.compare(a, b)) return 3;
    if(1 <= Integer.compare(a, b)) return 4;
    if(0 != Integer.compare(a, b)) return 5;
    return 0;
  }

  int testShort(short a, byte b) {
    if(Short.compare(a, b) < 0) return 1;
    if(Short.compare(a, b) >= 0) return 2;
    if(1 <= Short.compare(a, b)) return 4;
    return 0;
  }

  int testByte(int a, int b) {
    if(Byte.compare((byte)a, (byte)b) < 0) return 1;
    if(Byte.compare((byte)a, (byte)b) >= 1) return 2;
    return 0;
  }

  int testLong(long a, long b, long c, int d) {
    if(Long.compare(a + b, c + d) < 0) return 1;
    if(/*1*/Long/*2*/./*3*/compare/*4*/(/*5*/d /*6*/> 0 ? /*7*/a : b/*8*/, /*9*/c/*10*/) /*11*/< /*12*/0/*13*/) return 1;
    return 0;
  }
}