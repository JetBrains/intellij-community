import java.util.function.*;
class MyTest {
  <T> T foo(T t) {
    return t;
  }
  <T> T foo(Supplier<T> t) {
        return t.get();
    }

  <T> T foo(IntSupplier t) {
    return null;
  }

  static <K> K bar() {
    return null;
  }

  void m(int i) {
    String s = foo(switch (i) {default -> "str";});
    String s1 = <error descr="Incompatible types. Required String but 'foo' was inferred to T:
no instance(s) of type variable(s) exist so that Object conforms to String">foo(switch (i) {case 1 -> new Object(); default -> "str";});</error>
    String s2 =  foo(() -> switch (i) {
            default -> "str";
        });
    String s3 = foo(() -> switch (i) {default -> bar();});
    String s4 = foo(() -> switch (i) {default -> { break bar();}});
    String s5 = foo(<error descr="Incompatible types. Required String but 'foo' was inferred to T:
no instance(s) of type variable(s) exist so that Integer conforms to String">() -> switch (i) {default -> { break 1;}}</error>);
    String s6 = switch (i) {
      case 1 -> <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">2</error>;
      default -> {
        break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
      }
    };
    Supplier<String> stringSupplier = switch (i) {
            default -> {
                break () -> <error descr="Bad return type in lambda expression: int cannot be converted to String">1</error>;
            }
        };
    String s7 = switch (i) {
      case 1: {
        break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
      }
      default: {
        int i1 = switch (0) {
          default -> {
            break 1;
          }
        };
        break "";
      }
    };
  }

  void switchChain(final int i) {
    String s = switch (i) {
      default -> switch (0) {
        default -> {
          break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
        }
      };
    };
    String s1 = switch (i) {
      default -> {
        break switch (0) {
          default -> {
            break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
          }
        };
      }
    };

    String s2 = switch (i) {
      default: {
        break switch (0) {
          default -> {
            break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
          }
        };
      }
    };

    String s3 = switch (0) {
      default: {
        break switch (1) {
          case 2: {
            System.out.println();
            int inside_switch = switch (8) {
              default:
                break 1;
            };
          }
          case 1: 
            if (i > 3) break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">3</error>;
          case 0:
            try {
              break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">42</error>;
            } finally {
              //do nothing
            }
          default:
            break <error descr="Bad type in switch expression: int cannot be converted to java.lang.String">1</error>;
        };
      }
    };

    Runnable r = () -> <error descr="Target type for switch expression cannot be void">switch</error>(0) {
      default -> throw new IllegalArgumentException();
    };
  }

  static void test(boolean b, int i) {
    Class<?> c = (b ?
                  switch (i) {
                    case 1 -> true;
                    default -> 0;
                  } : 1).getClass();

    System.out.println(c.getCanonicalName());
  }
}