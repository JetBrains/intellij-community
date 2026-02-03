import java.util.function.*;

class Test {
  String nestedDefault(Integer i) {
    return switch (i) {
      case Integer ii -> {
        switch (i) {
          default -> {
            yield "fsdfs";
          }
        }
      }
    };
  }

  String nestedCaseDefault(Integer i) {
    return switch (i) {
      case Integer ii -> {
        switch (i) {
          default -> {
            yield "fsdfs";
          }
        }
      }
    };
  }

  String nestedUnconditionalPattern(Integer i) {
    return switch (i) {
      case Integer ii -> {
        switch (i) {
          case Integer iii -> {
            yield "fsdfs";
          }
        }
      }
    };
  }

  String nestedPattern(Object o) {
    return switch (o) {
       <error descr="Switch expression rule should produce a result in all execution paths">default</error> -> {
        switch (o) {
          case Integer i -> {
            System.out.println(); // completes normally
          }
          default -> {
            yield "fsdfsd";
          }
        }
      }
    };
  }

  // some javac tests

  String switchNestingExpressionStatement(Object o1, Object o2) {
    return switch (o1) {
      default -> {
        switch (o2) {
          case String s2 -> {
            yield "string, string";
          }
          default -> {
            yield "string, other";
          }
        }
      }
    };
  }

  String switchNestingStatementStatement(Object o) {
    return switch (o) {
      case String s1 -> {
        switch (o) {
          case String s2 -> {
            yield "string, string";
          }
          default -> {
            yield "string, other";
          }
        }
      }
      default -> {
        switch (o) {
          case String s2 -> {
            yield "other, string";
          }
          default -> {
            yield "other, other";
          }
        }
      }
    };
  }

  String switchNestingStatementExpression(Object o1, Object o2) {
    switch (o1) {
      case String s1 -> {
        return switch (o2) {
          case String s2 -> "string, string";
          default -> "string, other";
        };
      }
      default -> {
        return switch (o2) {
          case String s2 -> "other, string";
          default -> "other, other";
        };
      }
    }
  }

  String switchNestingExpressionExpression(Object o1, Object o2) {
    return switch (o1) {
      case String s1 ->
        switch (o2) {
          case String s2 -> "string, string";
          default -> "string, other";
        };
      default ->
        switch (o2) {
          case String s2 -> "other, string";
          default -> "other, other";
        };
    };
  }

  String switchNestingIfSwitch(Object o1, Object o2) {
    BiFunction<Object, Object, String> f = (n1, n2) -> {
      if (o1 instanceof CharSequence cs) {
        return switch (cs) {
          case String s1 ->
            switch (o2) {
              case String s2 -> "string, string";
              default -> "string, other";
            };
          default ->
            switch (o2) {
              case String s2 -> "other, string";
              default -> "other, other";
            };
        };
      } else {
        return switch (o2) {
          case String s2 -> "other, string";
          default -> "other, other";
        };
      }
    };
    return f.apply(o1, o2);
  }
}