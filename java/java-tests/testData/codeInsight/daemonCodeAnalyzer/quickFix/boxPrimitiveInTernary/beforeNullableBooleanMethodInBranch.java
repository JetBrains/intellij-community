// "Box primitive value in conditional branch" "true-preview"
class Test {
  public static void main(String strictArgument) {
    Boolean flag = Math.random() > 0.5 ? <caret>getBoolean() : false;
  }

  static Boolean getBoolean() {
    return Math.random() > 0.5 ? true : null;
  }
}