class SilhouetteScorePerfectValue {
  public int test(int num) {
    return 0;
  }

  public void doTest() {
    int a = 0;
    int b = 0;

    int testVar = test(a + b);
    int testVar1 = test(a + b);
    int testVar2 = test(a + b);
    int testVar3 = test(a + b);
    int testVar4 = test(a + b);


    test(b);
    test(b);
    test(b);
    test(b);
    test(b);
    test(b);
    test(b);
    test(b);
  }
}