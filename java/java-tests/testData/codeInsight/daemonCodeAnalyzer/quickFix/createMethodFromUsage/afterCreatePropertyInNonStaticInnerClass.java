// "Create property 'next' in 'InnerClass in MainClass'" "true-preview"
public class MainClass {
  void foo() {
    int counter = InnerClass.getNext();
  }

  private class InnerClass {

      private static int next;

      public static int getNext() {
          return next;
      }

      public static void setNext(int next) {
          InnerClass.next = next;
      }
  }
}