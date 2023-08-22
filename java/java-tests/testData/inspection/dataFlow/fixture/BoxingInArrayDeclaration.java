public class BoxingInArrayDeclaration {

  public static void main(String[] args) {
    Object[] arr = {1, 2};
    System.out.println(<warning descr="Condition 'arr[0] instanceof Integer' is always 'true'">arr[0] instanceof Integer</warning>);
  }
}