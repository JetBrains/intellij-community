// "Assert 'container != null'" "true-preview"
class A{
  void test(){
    Integer container = null;
    int i = 0;
    for (int limit = container.int<caret>Value(); i < limit; i++){}
  }
}