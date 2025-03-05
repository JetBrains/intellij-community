public class ConflictWithCast {
  public static void main(String[] args) {
    System.out.println(new Foo2("1", "2").a());
  }

  public record Foo2(String a<caret>, String b) {
  }
}