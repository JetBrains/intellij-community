// "Assert 'container != null'" "false"
class A{
  void test(){
    Integer container = null;
    for (int i = 0, limit = container.int<caret>Value(); i < limit; i++){}
  }
}