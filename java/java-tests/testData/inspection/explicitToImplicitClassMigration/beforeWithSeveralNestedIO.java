
<warning descr="Explicit class declaration can be converted into a compact source file">public class before<caret>WithSeveralNestedIO</warning> {
  public static void main(String[] args) {
    System.out.println((Runnable) () -> System.out.println("Hello"));
  }
}
