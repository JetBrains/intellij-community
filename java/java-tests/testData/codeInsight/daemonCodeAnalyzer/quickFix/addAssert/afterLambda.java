// "Assert 'container != null'" "true"
class A{
  void test(){
    Object container = null;
    Runnable r = () -> {
        assert container != null;
        container == null ? container.toString() : "";
    };
  }
}