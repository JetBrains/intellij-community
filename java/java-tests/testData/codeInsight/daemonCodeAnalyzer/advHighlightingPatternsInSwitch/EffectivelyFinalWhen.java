import java.util.function.*;

class Test {
  void test1(Object o, int mode) {
    switch (o) {
      case Integer i when i == <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> System.out.println();
        default -> {}
    }

    switch (o) {
      case Integer i when (switch (o) {
        case Integer ii when ii != <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> 2;
          case default -> 1;
      }) == <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> System.out.println();
        default -> {}
    }

    switch (o) {
      case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>) > 0 -> System.out.println();
        default -> {}
    }
    mode = 0;
  }

  void test2(Object o, final int mode) {
    switch (o) {
      case Integer i when (switch (<error descr="Variable used in guarded pattern should be final or effectively final">o</error>) {
        case Integer ii when ii != mode -> 2;
        case default -> 1;
      }) == mode -> o = null;
      default -> {}
    }
    switch (o) {
      case Integer i when (i = mode) > 0 -> System.out.println();
      default -> {}
    }
  }

  void test3(Object o, int mode) {
    switch (o) {
      case Integer i when i == mode -> System.out.println();
      default -> {}
    }
    switch (o) {
      case Integer i when (switch (o) {
        case Integer ii when ii != mode -> 2;
        case default -> 1;
      }) == mode -> System.out.println();
      default -> {}
    }
  }

  void testNested(Object o, Integer in) {
    switch (o) {
      case Integer mode when (mode = 42) > 9:
        switch (o) {
          case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>) > 0 -> System.out.println();
            default -> System.out.println();
        }
      default : break;
    }
    String str;
    str = switch (o) {
      case Integer mode when (mode = 42) > 9 ->
        switch (o) {
          case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>) > 0 -> "";
            default -> "";
        };
      default -> "";
    };
    str = switch (o) {
      case Integer mode when (mode = 42) > 9:
        yield switch (o) {
          case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>) > 0 -> "";
            default -> "";
        };
      default: yield "";
    };
    // lambdas
    str = switch (o) {
      case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">in</error>) > 0:
        yield ((Function<Integer, String>)(x) -> (<error descr="Variable used in lambda expression should be final or effectively final">in</error> = 5) > 0 ? "" : null).apply(in);
      default:
        yield "";
    };
    Consumer<Integer> c = (mode) -> {
      switch (o) {
        case Integer i when (i = <error descr="Variable used in guarded pattern should be final or effectively final">in</error>) > 0 -> System.out.println();
          default -> System.out.println();
      }
      <error descr="Variable used in lambda expression should be final or effectively final">in</error> = 1;
    };
    // try-with-resources
    try (<error descr="Variable used as a try-with-resources resource should be final or effectively final">in</error>) {
      switch (o) {
        case AutoCloseable ii when (<error descr="Variable used in guarded pattern should be final or effectively final">in</error> = ii) != null: break;
        default: break;
      }
    } catch (Exception e) {
    }
    // double nested
    switch (o) {
      case Integer mode when (mode = 42) > 9:
        switch (o) {
          case Integer i -> {
            switch (o) {
              case Integer ii when ii > <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>:
                break;
              default:
                break;
            }
          }
          default -> System.out.println();
        }
      default:
        break;
    }
    str = switch (o) {
      case Integer mode when (mode) > 9:
        yield switch (o) {
          case Integer i -> {
            yield switch (o) {
              case Integer ii when ii > mode: yield "";
              default: yield "";
            };
          }
          default -> "";
        };
      default: yield "";
    };
  }

  void declaredInWhenExpression(Object obj) {
    switch (obj) {
      case Integer i when new Function<Integer, Boolean>() {
        @Override
        public Boolean apply(Integer integer) {
          System.out.println(integer++);
          int num = 0;
          System.out.println(++num);
          return true;
        }
      }.apply(42) -> {}
      default -> {}
    }

    switch (obj) {
      case Integer i when switch (i) {
        case 1 -> {
          int num = 0;
          ++num;
          yield num;
        }
        default -> 42;
      } == 42 -> {}
      default -> {}
    }
  }
}