public class Foo {
  void test(int x) {
    if((x|0x1F)!=0x1F && ( x|0x38 )!=<caret>)
  }
}