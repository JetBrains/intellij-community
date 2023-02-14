// "Replace with lambda" "true-preview"
class Test {
  Runnable a = () -> {};
  Runnable r = new R<caret>unnable() {
    public void run() {
      a.run();
    }
  };
}