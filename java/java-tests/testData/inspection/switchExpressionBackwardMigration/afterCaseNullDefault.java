// "Replace with old style 'switch' statement" "true"

class CaseNullDefault {
  void bar(Object o) {
      switch (o) {
          case null:
          default:
              System.out.println("1");
              break;
      }
  }
}