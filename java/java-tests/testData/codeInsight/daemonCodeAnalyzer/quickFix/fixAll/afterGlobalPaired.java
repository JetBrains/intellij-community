// "Fix all 'Member access can be tightened' problems in file" "true"
class Test {
  private int myCounter;

  public static void main(String[] args) {
    System.out.println(new Test().myCounter);
  }
}