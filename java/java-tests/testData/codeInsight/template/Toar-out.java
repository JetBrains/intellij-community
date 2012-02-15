import java.util.*;

class Foo {
  {
    List<String> l;
    String[] s = l.toArray(new String[l.size()])<caret>
  }
}