// "Replace method reference with lambda" "true-preview"
class Test {
  {
    Runnable runnable = new Ru<caret>nnable() {
      {}
      public void run() {
        System.out.println(this);
      }
    }::run;
    runnable.run();
    runnable.run();
  }
}