// "Box primitive value in conditional branch" "true-preview"
class Test {
  public static void main(String strictArgument) {
    Integer test = Math.random() > 0.5 ? 1 :
                   Math.random() > 0.5 ? 2 : <caret>null;
  }
}