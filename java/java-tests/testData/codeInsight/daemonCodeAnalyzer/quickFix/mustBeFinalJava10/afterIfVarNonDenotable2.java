// "Copy 'x' to effectively final temp variable" "true-preview"
class Abc {
  interface A{}
  interface B{
    void m();
  }

  void test(Object obj) {
    var x = (A & B) obj;
    if (Math.random() > 0.5) {
      x = null;
    }
      var finalX = x;
      Runnable r = () -> {
      if (finalX != null) finalX.m();
    };
  }
}