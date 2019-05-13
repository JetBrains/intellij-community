// "Replace with 'a != null ?:'" "false"
class A{
  void test(){
    A a = null;
    <caret>a.field = 2;
  }

  int field;
}