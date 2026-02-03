// "Extract common part with variables from 'if'" "false"

class AutoCloseableSample {
  void foo(boolean a, boolean b) {
    if<caret> (a) {
      Data data = getData3();
      process(data);
    } else {
      Data data;
      if (b) {
        data = getData4();
      } else {
        data = getDefaultData();
      }
      process(data);
    }
  }
  private void process(Data data) {

  }
  interface Data {}
  private Data getData3() { return null; }
  private Data getData4() { return null; }
  private Data getDefaultData() { return null; }
}