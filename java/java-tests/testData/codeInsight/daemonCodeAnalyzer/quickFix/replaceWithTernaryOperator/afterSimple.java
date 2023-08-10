// "Replace with 'list != null ?:'" "true-preview"

class A{
  void test(){
    List list = Math.random() > 0.5 ? new List() : null;
    Object o = list != null ? list.get(0) : null<caret>;
  }
}