// "Box primitive value in conditional branch" "true-preview"
class Test {
  public static void main(String strictArgument) {
    Boolean strict = strictArgument == null ? false : strictArgument.isEmpty() ? true : <caret>null;
  }
}