public class WithoutConflictsForGet {
  public static void main(String[] args) {
    System.out.println(new Foo2("1", "2").b());
  }

  public record Foo2(String a<caret>, String b) {
  }
}