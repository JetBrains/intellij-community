class UnnecessaryCall_all {
  public static void main(String[] args) {
    String s = <caret>String.valueOf(1);
    s = 1 + String.valueOf(1);
    s = 1 + Character.toString('2');
    s = 2 + String.valueOf(1 + 1);
    s = 2 + "" + String.valueOf(1 + 1);
    s = String.valueOf(1 + 1).trim();
  }
}