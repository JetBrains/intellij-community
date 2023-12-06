class Test {
  void testDay1(Day d) {
    int a = 5;
    switch (d) {
      case MONDAY:
        break;
       default:
        a = -10;
        break;
    }
    if (a < -1) {
      System.out.println();
    }
  }

  void testDay2(Day d) {
    int a = 5;
    switch (d) {
      case MONDAY:
        break;
       default:
        a = 10;
        break;
    }
    if (<warning descr="Condition 'a < -1' is always 'false'">a < -1</warning>) {
      System.out.println();
    }
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}
