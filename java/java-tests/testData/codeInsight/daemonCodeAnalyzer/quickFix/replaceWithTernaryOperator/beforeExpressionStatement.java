// "Replace with 'list != null ?:'" "false"
class A{
  void test(){
    List list = null;
    li<caret>st.get(0);
  }
}