// "Replace with old style 'switch' statement" "true-preview"

class GuardedExpressions {
  void foo2(Object o) {
      switch (o) {
          case Integer s when s.hashCode() == 1:
              System.out.println(1);
              break;
          default:
              System.out.println(2);
              break;
      }
  }
}