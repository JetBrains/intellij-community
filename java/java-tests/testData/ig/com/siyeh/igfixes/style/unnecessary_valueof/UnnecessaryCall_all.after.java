class UnnecessaryCall_all {
  public static void main(String[] args) {
    String s = "" + 1;
    s = 1 + "" + 1;
    s = 1 + "" + '2';
    s = 2 + "" + (1 + 1);
    s = 2 + "" + (1 + 1);
    s = ("" + (1 + 1)).trim();
  }
}