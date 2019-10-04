class C {
  {
    boolean has<caret>Three = false;
    for (int i = 1; i <= 5; i++) {
      hasThree |= i == 3;
    }
    System.out.println(hasThree);
  }
}
