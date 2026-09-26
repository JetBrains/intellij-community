// "Replace with old style 'switch' statement" "true-preview"

class UnnamedGuardedExpressions {
  void foo3(Object o) {
    switc<caret>h (o) {
      case Integer _, String _ when o.hashCode() == 1 -> System.out.println(1);
      default -> System.out.println(2);
    }
  }
}