
import java.util.Map;

class Temp {
  class Set {
    native Object size();
  }
  
  public Object foo(Set bar) {
    if (bar.size() < 2)
      return <caret>abortWithCorruptDataError("" + bar.size()); // Inline this

    return bar;
  }

  public static Map<String, Object> abortWithCorruptDataError(Object message) { // or online this
    return null;
  }
}