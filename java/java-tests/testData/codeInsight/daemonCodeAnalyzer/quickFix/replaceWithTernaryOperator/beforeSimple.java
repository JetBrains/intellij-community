// "Replace with 'list != null ?:'" "true"

class A{
  void test(){
    List list = null;
    Object o = li<caret>st.get(0);
  }
}