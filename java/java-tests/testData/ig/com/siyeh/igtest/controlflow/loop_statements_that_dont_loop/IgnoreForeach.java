import java.util.*;

class IgnoreForeach {

  Number first(List<Number> numbers) {
    for (Number number : numbers) {
      return number;
    }
    return null;
  }
}