// "Box primitive value in conditional branch" "true-preview"
class Test {
  public static void main(String strictArgument) {
    Integer test = Math.random() > 0.5 ? Integer.valueOf(1) :
                   Math.random() > 0.5 ? 2 : null;
  }
}