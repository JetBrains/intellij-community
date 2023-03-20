// "Split into declaration and assignment" "true-preview"
class Test {
  {
      for (int i<caret>//c1
    =0; i<10; i++) {
          System.out.println();
      }
  }
}