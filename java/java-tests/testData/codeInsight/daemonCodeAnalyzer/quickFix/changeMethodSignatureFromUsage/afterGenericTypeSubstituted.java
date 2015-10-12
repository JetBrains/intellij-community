// "Remove 2nd parameter from method 'update'" "true"
class Test<T> {

  void update(T a) {}

  public static void main(String[] args) {
    Test<String> instance = new Test<>();
    instance.update("string1");
  }
}