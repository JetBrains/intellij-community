class Main {
  private static void printBool(boolean value) {
    //
  }

  public static void main(String... args) {
    <caret>  printBool(args.length != 0);
  }
}