// "Replace with anyMatch()" "false"

import java.util.List;

public class Main {
  public boolean testPrimitiveMap(List<String> data) {
    for(String str : d<caret>ata) {
      if(str.startsWith("xyz")) {
        float len = str.length();
        if(len > 10) {
          return true;
        }
      }
    }
    return false;
  }
}