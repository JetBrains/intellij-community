class Printer {
  void printLines(String intro, String message) {
    System.out.println(intro);
    System.out.println(message);
  }

  public static void main(String[] args) {
    new Printer().printLines("Hello", "World"); // literal
    new Printer().printLines(args[0], args[1]); // non-literal
  }
}