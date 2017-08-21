// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public boolean testPrimitiveMap(List<String> data) {
    for(String str : d<caret>ata) {
      if(str.startsWith("xyz")) {
        int len = str.length();
        if(len > 10) {
          return true;
        }
      }
    }
    return false;
  }
}