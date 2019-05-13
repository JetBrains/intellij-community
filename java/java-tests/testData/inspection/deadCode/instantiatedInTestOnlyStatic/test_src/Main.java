public class Main {
  public static void main(String[] args) {
    Test.doSmth();
  }

  public static void unusedInTestScope() {
    System.out.println("hey");
  }
}