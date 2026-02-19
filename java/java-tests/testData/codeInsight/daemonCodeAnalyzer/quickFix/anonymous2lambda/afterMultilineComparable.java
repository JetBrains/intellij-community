// "Replace with lambda" "true-preview"
class Test {
  {
    Comparable<String> c = o -> {
      System.out.println();
      return 0;
    }; 
  }
}