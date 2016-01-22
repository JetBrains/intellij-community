// "Replace with 'list != null ?:'" "true"

class A{
  void test(){
    List list = null;
    Object o = list.ge<caret>t(0);
  }
}