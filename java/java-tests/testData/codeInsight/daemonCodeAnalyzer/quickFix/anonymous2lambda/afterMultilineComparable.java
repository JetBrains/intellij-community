// "Replace with lambda" "true"
class Test {
  {
    Comparable<String> c = o -> {
      System.out.println();
      return 0;
    }; 
  }
}