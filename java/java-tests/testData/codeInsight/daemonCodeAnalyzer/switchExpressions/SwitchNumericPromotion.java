
class SwitchExpressions {

  byte B = 1;
  short S = 1;
  char C = 1;
  final int I = 1;

  void m(int i) {
    var v1 = switch(i) {
      case 1 -> 1;
      default -> 1.0;
    };

    double d = v1;
    float f = <error descr="Incompatible types. Found: 'double', required: 'float'">v1</error>;
    int in = <error descr="Incompatible types. Found: 'double', required: 'int'">v1</error>;

    var v2 = switch (i) {
      case 1 -> C;
      default -> I;
    };

    in = v2;
    byte b = <error descr="Incompatible types. Found: 'char', required: 'byte'">v2</error>;
    short s = <error descr="Incompatible types. Found: 'char', required: 'short'">v2</error>;
    char c = v2;

    var v3 = switch (i) {
      case 1 -> B;
      default -> I;
    };

    in = v3;
    b = v3;
    s = v3;
    <error descr="Incompatible types. Found: 'byte', required: 'char'">c = v3</error>;

    var v4 = switch (i) {
      case 1 -> B;
      default -> Integer.MAX_VALUE;
    };

    in = v4;
    <error descr="Incompatible types. Found: 'int', required: 'byte'">b = v4</error>;
    <error descr="Incompatible types. Found: 'int', required: 'short'">s = v4</error>;
    <error descr="Incompatible types. Found: 'int', required: 'char'">c = v4</error>;
  }
}