// "Box primitive value in conditional branch" "false"
class Test {
  public static void main(String strictArgument) {
    boolean strict = strictArgument == null ? false : strictArgument.isEmpty() ? true : <caret>null;
  }
}