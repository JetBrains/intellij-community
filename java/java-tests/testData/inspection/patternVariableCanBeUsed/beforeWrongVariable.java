// "Replace 'number' with pattern variable" "false"
class X {
  public static void main(String[] args) {
    Object o1 = 1.0;
    Object o2 = 2;
    if (!(o1 instanceof Double)) return;
    Number n<caret>umber = (Number) o2;
    System.out.println("number = " + number);
  }
}