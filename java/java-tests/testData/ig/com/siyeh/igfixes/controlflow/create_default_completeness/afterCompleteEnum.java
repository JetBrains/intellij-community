// "Create 'default' branch" "true"
class Test {
  void test(Day d) {
    switch (d){
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
        default:
            throw new IllegalStateException("Unexpected value: " + d);
    }
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}