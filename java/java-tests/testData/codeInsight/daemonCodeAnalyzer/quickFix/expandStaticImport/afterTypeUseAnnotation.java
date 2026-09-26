// "Replace static import with qualified access to NlsActions" "true-preview"
package foo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

class Test {
  List<@NlsActions.ActionDescription String> list;
}
class NlsActions {
  @Target(ElementType.TYPE_USE)
  public @interface ActionDescription {
  }
}