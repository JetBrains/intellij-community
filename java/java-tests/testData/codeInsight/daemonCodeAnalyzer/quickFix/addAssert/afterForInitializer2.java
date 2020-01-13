// "Assert 'container != null'" "true"
class A{
  void test(){
    Integer container = null;
    int i = 0;
      assert container != null;
      for (int limit = container.intValue(); i < limit; i++){}
  }
}