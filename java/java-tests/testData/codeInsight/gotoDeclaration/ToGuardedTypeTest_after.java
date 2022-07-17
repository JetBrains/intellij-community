class Test{
  void foo(Object o) {
    switch(o){
      case String <caret>s when s.length() > 0 -> System.out.println(s);
      default -> System.out.println()
    }
  }
}
