// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

public class Main<T> {
  public static <A extends Annotation> Optional<A> findAnnotation(Optional<? extends AnnotatedElement> element) {
    if (element.isPre<caret>sent()) {
      return Optional.empty();
    }
    return findAnnotation((AnnotatedElement)null);
  }

  private static <A extends Annotation> Optional<A> findAnnotation(AnnotatedElement element) {
    return Optional.empty();
  }
}