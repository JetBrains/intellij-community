// "Convert to atomic" "true"
class A {
  long <caret>x = 0;

  public void testAtomicLong() {
    x++;
    x--;
    x+=2;
    x-=2;
    x*=3;
    x/=3;
    x%=3;
    x&=3;
    x|=3;
  }
}