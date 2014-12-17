// "Replace with lambda" "true"
class Test {
  Runnable r = new R<caret>unnable() {
    public void run() {
      "".toString();
    }
  };
}