// "Replace with lambda" "true-preview"
class Test {
  {
    String o = "";
    Comparable<String> c = new Compa<caret>rable<String>() {
      @Override
      public int compareTo(String o) {
        return o.length();
      }
    }; 
  }
}