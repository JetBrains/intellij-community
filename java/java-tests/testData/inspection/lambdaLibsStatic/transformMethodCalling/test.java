import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.transf<caret>orm(new ArrayList<>(), getFunction())
  }

  Function<String, String> getFunction() {
    return new Function<String, String>() {

    }
  }
}