import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class A {
  static void m()  {
    String str = FluentIterable.fr<caret>om(new ArrayList<String>()).get(0);
  }
}
