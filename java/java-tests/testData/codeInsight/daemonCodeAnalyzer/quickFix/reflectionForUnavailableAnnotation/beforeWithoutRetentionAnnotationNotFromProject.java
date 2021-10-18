// "Annotate annotation 'Subst' as @Retention" "false"
import org.intellij.lang.annotations.Subst;

import java.lang.reflect.Method;

class Foo {
  boolean bar(Method method) {
    return method.getAnnotation(Subst.class<caret>) != null;
  }
}