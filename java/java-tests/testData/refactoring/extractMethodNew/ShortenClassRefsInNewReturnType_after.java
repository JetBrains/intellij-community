import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

class Test {
  Object foo() {
    Object list = newMethod();
    return list;
  }

    @NotNull
    private Collection newMethod() {
        return new ArrayList<String>();
    }
}