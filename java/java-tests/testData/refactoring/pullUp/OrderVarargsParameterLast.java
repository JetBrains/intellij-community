class Super {}
class Test extends Super{
  String[] f2;
  String   f<caret>1;

  public Test(String f1, String... f2) {
    this.f2 = f2;
    this.f1 = f1;
  }
}