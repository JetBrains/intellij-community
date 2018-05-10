// "Introduce local variable" "false"
class a {
  Runnable a() {
    return new Runnable() {
      public void run() {
        if(2+<caret>2 == 5) {}
      }
    };
  }
}

