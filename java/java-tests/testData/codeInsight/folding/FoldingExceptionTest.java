class Test {
  int b;
  java.util.List<String> values = new java.util.ArrayList<String>();

  int getB() {
<caret>    java.util.List<String> values = new java.util.ArrayList<String>();
    return b;
  }
}
