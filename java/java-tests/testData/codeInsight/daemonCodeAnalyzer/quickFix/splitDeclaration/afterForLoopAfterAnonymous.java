// "Split into declaration and assignment" "true-preview"
class Test {
  {
      int i;
      for (i = 0; i<10; i++) {
          System.out.println();
      }

      new Runnable() {
        {
          int i = 0;
        }
      };
  }
}