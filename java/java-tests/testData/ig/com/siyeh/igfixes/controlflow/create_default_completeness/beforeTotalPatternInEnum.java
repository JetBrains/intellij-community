// "Create 'default' branch" "false"
class Test {
  void test(Day d) {
    switch (<caret>d){
      case Day dd:
        break;
    }
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}