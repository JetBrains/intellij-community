import com.google.common.collect.FluentIterable;
import java.util.ArrayList;

public class Main20 {
  void get() {
    FluentIterable<String> i = FluentIterable.from(new ArrayList<String>());
    if (i.tran<caret>sform(String::isEmpty).first().orNull()) {
      System.out.println(String.format("asd %s zxc", i));
    }
  }
}