// "Replace with old style 'switch' statement" "true-preview"

class UnnamedGuardedExpressions {
  void foo3(Object o) {
      switch (o) {
          case Integer _, String _ when o.hashCode() == 1:
              System.out.println(1);
              break;
          default:
              System.out.println(2);
              break;
      }
  }
}