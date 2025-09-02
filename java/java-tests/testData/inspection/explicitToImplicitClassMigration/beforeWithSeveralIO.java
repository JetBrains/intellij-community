
<warning descr="Explicit class declaration can be converted into a compact source file">public class before<caret>WithSeveralIO</warning> {
  public static void main(String[] args) {
    System.out.println("Hello, world!");
    System.out.println("Hello, world!");
    System.out.println("Hello, world!");
    System.out.print("Hello, world!");
  }
}
