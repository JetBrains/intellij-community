// "Remove 2nd parameter from method 'update'" "true-preview"
class Test<T> {

  void update(T a, String b) {}

  public static void main(String[] args) {
    Test<String> instance = new Test<>();
    instance.update("string<caret>1");
  }
}