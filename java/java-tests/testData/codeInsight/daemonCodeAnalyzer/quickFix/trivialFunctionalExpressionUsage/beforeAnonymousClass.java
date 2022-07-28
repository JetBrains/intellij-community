// "Replace call with method body" "true-preview"

class Test {
  {
    new Runnable() {
      public void run() {
        System.out.println();
      }
    }.ru<caret>n();
  }
}