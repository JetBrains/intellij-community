// "Assert 'container != null'" "true-preview"
class A{
  void test(){
    Integer container = Math.random() > 0.5 ? null : 1.0;
    int i = 0;
    for (int limit = container.int<caret>Value(); i < limit; i++){}
  }
}