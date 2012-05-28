// "Replace with 'list != null ?:'" "true"

class A{
  void test(){
    List list = null;
    Object o = list != null ? list.get(0) : <selection>null</selection>;
  }
}