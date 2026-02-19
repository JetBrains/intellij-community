class X {

  public static void main(String[] args) {
    int x = 1;
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(1);
      stringBuilder.append(2);
      final String s = stringBuilder.toString();
    System.out.println("x = " + x);
  }
}