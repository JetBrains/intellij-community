class C {

  int calculate(int one, int two, int three) {
      while (true) {
          int one1 = one;
          one = three + two;
          two = two + one1;
          three = one1;
      }
  }
}