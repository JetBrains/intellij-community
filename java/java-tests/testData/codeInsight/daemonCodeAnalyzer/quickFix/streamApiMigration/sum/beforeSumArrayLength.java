// "Replace with sum()" "true"

public class Main {
  public long test(String[] array) {
    long i = 0;
    for(String a : ar<caret>ray) {
      if(a.startsWith("xyz"))
        i = i + a.length();
    }
    return i;
  }
}