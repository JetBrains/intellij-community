// "Annotate annotation 'Test' as @Retention(RetentionPolicy.RUNTIME)" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Target(ElementType.METHOD)
@interface Test {
}

class Main {
  private static boolean hasTestAnnotation(Method method) {
    return method.getAnnotation(Test.class<caret>) != null;
  }
}