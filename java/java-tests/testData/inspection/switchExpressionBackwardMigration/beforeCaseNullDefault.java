// "Replace with old style 'switch' statement" "true"

class CaseNullDefault {
  void bar(Object o) {
    switc<caret>h (o) {
      case null, default -> System.out.println("1");
    }
  }
}