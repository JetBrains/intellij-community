
class Test {
  public static void main(String[] args) {
    method("John", "Doe");
  }
  
  public static void foo(Person person) {
    System.out.println("first: " + person.getFirstName());
    System.out.println("last: " + person.getLastName());
  }
}