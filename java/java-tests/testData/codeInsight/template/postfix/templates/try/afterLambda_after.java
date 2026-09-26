class Hello {
  public static void main(String[] args) {
      try {
          new Thread(() -> {
            int a = 1;
      
            int b = 2;
      
            int c = 3;
          }).start();<caret>
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
      int d = 4;
  }
}