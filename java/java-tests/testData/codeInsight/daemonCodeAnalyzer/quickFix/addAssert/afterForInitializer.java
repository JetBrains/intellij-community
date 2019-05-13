// "Assert 'container != null'" "true"
class A{
  void test(){
    Integer container = null;
      assert container != null;
      for (int i = 0, limit = container.intValue(); i < limit; i++){}
  }
}