import java.util.*;

class Foo {
  {
    List<String> l;
    String[] s = l.toArray(new String[0])<caret>
  }
}