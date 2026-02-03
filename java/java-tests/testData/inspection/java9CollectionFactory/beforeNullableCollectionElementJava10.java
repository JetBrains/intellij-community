// "Replace with 'List.copyOf()' call" "false"
import org.jetbrains.annotations.*;
import java.util.*;

class Scratch {
  public static void main(String[] args) {
    var data = new ArrayList<@Nullable String>();
    data.add("foo");
    data.add("bar");
    data.add(null);

    var list = Collections.<caret>unmodifiableList(new ArrayList<>(data));

    System.out.println(list);
  }
}