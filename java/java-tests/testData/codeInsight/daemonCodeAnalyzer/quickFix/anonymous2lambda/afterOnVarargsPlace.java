// "Replace with lambda" "true-preview"
class Test2 {

  void f(Runnable... rs){}
  {
    f(null, () -> {

    });
  }
}
