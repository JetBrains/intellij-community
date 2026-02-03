class C {

  int calculate(int one, int two, int three) {
      while (true) {
          three = one + two + three;
          two = one * two;
          one = one ^ 2;
      }
  }
}