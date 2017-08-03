// "Replace with sum()" "false"

public class Main {
  public long test(String[] array) {
    float i = 0;
    for(String a : ar<caret>ray) {
      if(a.startsWith("xyz"))
        i = i + a.length();
    }
    return i;
  }
}