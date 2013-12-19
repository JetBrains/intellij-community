// "Assert 'container != null'" "true"
class A{
  void test(){
    Integer container = null;
    for (int i = 0, limit = con<caret>tainer.intValue(); i < limit; i++){}
  }
}