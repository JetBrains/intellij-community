// "Replace with lambda" "true-preview"
class Test {
  Runnable r = new R<caret>unnable() {
    public void run() {
      "".toString();
    }
  };
}