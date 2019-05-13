// "Assert 'container != null'" "true"
class A{
  void test(){
    Object container = null;
    Runnable r = () -> container == null ? container.toS<caret>tring() : "";
  }
}