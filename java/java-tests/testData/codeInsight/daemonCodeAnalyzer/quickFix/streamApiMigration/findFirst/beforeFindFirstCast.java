// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public String testCast(List<Object> data) {
    for (Object obj : d<caret>ata) {
      if(obj instanceof String) {
        return (String)obj;
      }
    }
    return null;
  }
}