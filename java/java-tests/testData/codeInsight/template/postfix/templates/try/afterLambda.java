class Hello {
  public static void main(String[] args) {
    new Thread(() -> {
      int a = 1;

      int b = 2;

      int c = 3;
    }).start();.try<caret>
    int d = 4;
  }
}