class OperatorAssignment {
    public static void main(String[] args) {
      int a = 10;
      double b = 0.5;

      b += <caret>a;

      System.out.println(b);
    }
}