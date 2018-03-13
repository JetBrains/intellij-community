public class TwoWords {
  public void test() {
    int someWord = 0, theOtherWord = 1, someWord123 = 2;
    int <warning descr="'someWord' should probably not be assigned to 'x'">x</warning> = someWord;
    int <warning descr="'theOtherWord' should probably not be assigned to 'y'">y</warning> = theOtherWord;
    someWord123 = someWord;
    theOtherWord = someWord;
  }
}
