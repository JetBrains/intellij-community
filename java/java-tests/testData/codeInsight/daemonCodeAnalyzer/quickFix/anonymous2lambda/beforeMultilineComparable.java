// "Replace with lambda" "true-preview"
class Test {
  {
    Comparable<String> c = new Compa<caret>rable<String>() {
      @Override
      public int compareTo(String o) {
        System.out.println();
        return 0;
      }
    }; 
  }
}