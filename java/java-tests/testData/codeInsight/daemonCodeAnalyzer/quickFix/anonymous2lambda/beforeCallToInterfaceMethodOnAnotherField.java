// "Replace with lambda" "true"
class Test {
  Runnable a = () -> {};
  Runnable r = new R<caret>unnable() {
    public void run() {
      a.run();
    }
  };
}