// "Replace with 'list != null ?:'" "true-preview"

class A{
  void test(){
    List list = Math.random() > 0.5 ? new List() : null;
    Object o = list.ge<caret>t(0);
  }
}