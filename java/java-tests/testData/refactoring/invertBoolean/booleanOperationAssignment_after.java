class C {
  {
    boolean hasThreeInverted = true;
    for (int i = 1; i <= 5; i++) {
      hasThreeInverted &= i != 3;
    }
    System.out.println(!hasThreeInverted);
  }
}
