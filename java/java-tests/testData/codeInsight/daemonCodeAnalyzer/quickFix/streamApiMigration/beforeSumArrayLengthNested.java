// "Replace with sum()" "true"

public class Main {
  public double test(String[][] array) {
    double d = 10;
    for(String[] arr : arra<caret>y) {
      if(arr != null) {
        for (String a : arr) {
          if (a.startsWith("xyz"))
            d = d + 1.0/a.length();
        }
      }
    }
    return d;
  }
}