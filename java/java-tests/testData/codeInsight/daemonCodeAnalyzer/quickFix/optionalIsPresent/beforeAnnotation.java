// "Replace Optional presence condition with functional style expression" "true-preview"

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

public class Main<T> {
  public static <A extends Annotation> Optional<A> findAnnotation(Optional<? extends AnnotatedElement> element) {
    if (element.isPre<caret>sent()) {
      return element.get().getAnnotations().length == 0 ? Optional.empty() : null;
    }
    return findAnnotation((AnnotatedElement)null);
  }

  private static <A extends Annotation> Optional<A> findAnnotation(AnnotatedElement element) {
    return Optional.empty();
  }
}