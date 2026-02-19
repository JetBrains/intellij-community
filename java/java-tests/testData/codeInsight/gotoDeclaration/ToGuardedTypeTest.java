class Test{
  void foo(Object o) {
    switch(o){
      case String s when s.length() > 0 -> System.out.println(s<caret>);
      default -> System.out.println()
    }
  }
}
