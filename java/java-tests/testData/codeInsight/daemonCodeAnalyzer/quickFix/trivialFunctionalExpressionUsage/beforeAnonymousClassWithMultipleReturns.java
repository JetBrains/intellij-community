// "Replace call with method body" "false"

class Test {
  {
    new Runnable() {
      public void run() {
        if (true) {
          System.out.println();
          return;
        }
        else {
          System.out.println();
          return;
        }
      }
    }.ru<caret>n();
  }
}