class Main {
  static class X {
    int f() { return 0; }
  }

  int switchTest(Object o) {
    int i1 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case X x -> x.f();
      default -> 1;
    };
    int i2 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case null, X x -> x.f();
      default -> 1;
    };

    int i3 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case X x, null -> x.f();
      default -> 1;
    };

    int i4 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case String s, X x -> x.f();
      default -> 1;
    };

    int i5 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case String s, X x -> x.f();
      default -> 1;
    };

    int i6 = switch(<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">o</error>) {
      case X x, String s -> x.f();
      default -> 1;
    };
    return i1 + i2 + i3 + i4 + i5 + i6;
  }
}
