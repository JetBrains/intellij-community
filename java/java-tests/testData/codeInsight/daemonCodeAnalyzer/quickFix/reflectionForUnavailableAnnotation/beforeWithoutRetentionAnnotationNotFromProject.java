// "Annotate annotation 'Subst' as @Retention(RetentionPolicy.RUNTIME)" "false"
import org.intellij.lang.annotations.Subst;

import java.lang.reflect.Method;

class Foo {
  boolean bar(Method method) {
    return method.getAnnotation(Subst.class<caret>) != null;
  }
}