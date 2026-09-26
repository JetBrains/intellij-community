import org.jetbrains.annotations.*;

class A {
  double widen(int x) {
    return (<warning descr="Casting 'x' to 'double' is redundant">double</warning>) x;
  }
  
  double widenDataLoss(long x) {
    return (double) x;
  }
  
  void call(short a, byte b, long c) {
    widen((<warning descr="Casting 'a' to 'int' is redundant">int</warning>)a);
    widen((<warning descr="Casting 'b' to 'int' is redundant">int</warning>)b);
    widen((int)c);
  }
  
  Object test(int x) {
    return (long) x;
  }
}