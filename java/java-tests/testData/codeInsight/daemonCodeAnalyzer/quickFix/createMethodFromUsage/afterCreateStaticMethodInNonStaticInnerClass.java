// "Create method 'getNext' in 'InnerClass in MainClass'" "true-preview"
public class MainClass {
  void foo() {
    int counter = InnerClass.getNext();
  }

  private class InnerClass {

      public static int getNext() {
          return 0;
      }
  }
}