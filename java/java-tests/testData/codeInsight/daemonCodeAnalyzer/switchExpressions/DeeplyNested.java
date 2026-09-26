class X {
  class A {
    A a;
  }

  void main() {
    A a = new A();
    a.a = a;
    System.out.println(switch (a) {
      case null -> null;
      case A b -> switch (b.a) {
        case null -> null;
        case A c -> switch (c.a) {
          case null -> null;
          case A d -> switch (d.a) {
            case null -> null;
            case A e -> switch (e.a) {
              case A f -> f.toString();
              case null -> null;
            };
          };
        };
      };
    });
  }
}