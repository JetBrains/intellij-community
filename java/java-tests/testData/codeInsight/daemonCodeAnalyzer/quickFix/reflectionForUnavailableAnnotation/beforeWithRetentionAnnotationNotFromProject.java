// "Annotate annotation 'NotNull' as @Retention(RetentionPolicy.RUNTIME)" "false"
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

class Foo {
  boolean bar(Method method) {
    return method.getAnnotation(NotNull.class<caret>) != null;
  }
}