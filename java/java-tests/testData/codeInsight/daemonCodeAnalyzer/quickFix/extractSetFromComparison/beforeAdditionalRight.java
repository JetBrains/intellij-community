// "Extract Set from comparison chain" "true"
public class Test {
  interface Person {
    String getName();
  }

  void testOr(Person person) {
    if("foo".equals(person.getName()) || "bar".equals(person.getName()) || "baz".equals(person.getName()<caret>) || person.getName() == null) {
      System.out.println("foobarbaz");
    }
  }
}
