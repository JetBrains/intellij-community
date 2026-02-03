  class Test {
     public void m() {
       new Runnable() {
           public void run() {
              <caret>
           }
       };
     }
  }
