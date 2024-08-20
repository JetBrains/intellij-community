// "Create 'default' branch" "true"
class Test {
  void test(Day d) {
    switch (<caret>d){
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
    }
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}