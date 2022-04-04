// "Box primitive value in conditional branch" "true"
class Test {
  public static void main(String strictArgument) {
    Boolean strict = strictArgument == null ? Boolean.FALSE : strictArgument.isEmpty() ? true : null;
  }
}