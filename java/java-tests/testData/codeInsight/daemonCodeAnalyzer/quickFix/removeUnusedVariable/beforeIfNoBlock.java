// "Remove local variable 'foo'" "true-preview"
class a {
  private int refactorTest(int i) {
    int f<caret>oo = 0;
    int bar = 0;
    if (i >0) foo++;
    return bar;
  }
}

