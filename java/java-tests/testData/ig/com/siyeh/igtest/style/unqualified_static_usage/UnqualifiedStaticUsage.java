enum UnqualifiedStaticUsage {
  RED, BLUE;

  int switches(UnqualifiedStaticUsage c) {
    System.out.println(<warning descr="Unqualified static field access 'RED'">RED</warning>);
    System.out.println(UnqualifiedStaticUsage.BLUE);
    switch (c) {
      case RED: break;
      case BLUE: break;
    }
    return switch (c) {
      case RED -> 1;
      case <error descr="Constant expression required">(BLUE)</error> -> 2;
    };
  }
}

