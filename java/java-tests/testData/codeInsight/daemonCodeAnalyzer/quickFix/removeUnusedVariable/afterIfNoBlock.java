// "Remove local variable 'foo'" "true-preview"
class a {
  private int refactorTest(int i) {
      int bar = 0;
    if (i >0) ;
    return bar;
  }
}

