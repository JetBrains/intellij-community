// "Remove 2nd parameter from method 'update'" "true-preview"
class Test<T> {

  void update(T a) {}

  public static void main(String[] args) {
    Test<String> instance = new Test<>();
    instance.update("string1");
  }
}