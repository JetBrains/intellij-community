// "Split into declaration and assignment" "false"
class Test {
  {
      for (int i<caret>=0; i<10; i++) {
          System.out.println();
      }
      Runnable r = () -> {int i = 0;};
  }
}