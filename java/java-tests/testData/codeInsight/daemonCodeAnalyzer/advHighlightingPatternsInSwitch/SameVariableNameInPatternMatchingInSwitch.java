class Main {

  void statement(Object o) {
    switch (o) {
      case Integer n && n > 1:
        break;
      case Integer n && n < 1:
        break;
      default:
        break;
    }
  }

  int expression(Object o) {
    return switch (o) {
      case Integer n && n > 1 -> n;
      case Integer n && n < 1 -> n;
      default -> 0;
    };
  }

  int nestedExpression(Object o, Object o2, int p) {
    int m = 0;
    return switch (o) {
      case Integer n && n > 1 -> switch(o2) {
        case Integer <error descr="Variable 'm' is already defined in the scope">m</error> && m > 0 -> m + n;
        case Integer <error descr="Variable 'p' is already defined in the scope">p</error> -> p + n;
          default -> 0;
      };
      case Integer n && n < 1 -> n;
      default -> 0;
    };
  }

  void nestedStatement(Object o, Object o2, int p) {
    int m = 0;
    switch (o) {
      case Integer n && n < 1:
        n ++;
      case Integer n && n > 1:
        switch(o2) {
          case Integer <error descr="Variable 'm' is already defined in the scope">m</error> && m > 0:
            m += n;
          case Integer <error descr="Variable 'p' is already defined in the scope">p</error> && p > 0:
            p += n + m;
            break;
          case Integer p1:
            p += n + m + p1;
        };
        break;
    };
  }
}