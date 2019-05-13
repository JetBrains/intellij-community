class Test {

  // IDEA-182933
  public static void main(String[] args) {
    int a = 1;
    int b = 2;
    boolean f = false;

    String s = "Hello " + (a + b) + " World";

    System.out.println(s); // Prints Hello 3 World
  }
}