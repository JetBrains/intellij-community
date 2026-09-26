
class Test {
  public static void main(String[] args) {
    method("John", "Doe");
  }
  
  public static void foo(FullName fullName) {
    System.out.println("first: " + fullName.firstName());
    System.out.println("last: " + fullName.lastName());
  }
}