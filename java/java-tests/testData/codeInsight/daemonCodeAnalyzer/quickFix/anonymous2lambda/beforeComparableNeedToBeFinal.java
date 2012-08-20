// "Replace with lambda" "true"
class Test {
  {
    Comparable<String> c = new Compa<caret>rable<String>() {
      @Override
      public int compareTo(final String o) {
        Runnable r = new Runnable() {
          @Override
          public void run() {
            System.out.println(o);
          }
        }; 
        return 0;
      }
    }; 
  }
}