public class GenericsSmartCast<T>{
  public GenericsSmartCast(GenericsSmartCast<T> other) {}

  public static Object foo() { return null; }

  public static void main(String[] args) {
    GenericsSmartCast<String> bar = new GenericsSmartCast<String>((<caret>) foo());
  }
}