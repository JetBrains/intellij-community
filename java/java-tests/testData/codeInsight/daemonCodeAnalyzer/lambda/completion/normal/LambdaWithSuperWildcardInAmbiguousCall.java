import java.util.stream.*;
import java.util.*;

class Foo {
  public static void main(List<String> list) {
    list.stream().collect(Collectors.toMap(o -> o.sub<caret>))
  }
}