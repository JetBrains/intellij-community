class Main {
  private static void printBool(boolean value) {
    //
  }

  public static void main(String... args) {
    <caret>if (args.length != 0) {
      printBool(true);
    } else {
      printBool(false);
    }
  }
}