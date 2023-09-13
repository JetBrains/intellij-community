class Test {
  void insideSwitch(Object o) {
    switch (o){
      case /*unused*/ Object <warning descr="Pattern variable 's' is never used"><caret>s</warning> /*unused*/ -> System.out.println();
    }
    switch (o){
      case Object s -> System.out.println(s);
    }
    switch (o) {
      case <error descr="Parenthesized patterns are not supported at language level '21'">(Object s)</error> when s != null -> System.out.println();
      default -> System.out.println();
    }
  }

  void insideInstanceOf(Object o) {
    if (o instanceof String s && s != null) {
      System.out.println();
    }
    if (o instanceof String s) {
      System.out.println(s);
    }
    if (o instanceof String <warning descr="Pattern variable 's' is never used">s</warning>) {
      System.out.println();
    }
  }
}