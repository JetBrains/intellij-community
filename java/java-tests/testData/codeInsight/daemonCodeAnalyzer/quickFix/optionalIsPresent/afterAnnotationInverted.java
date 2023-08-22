// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

public class Main<T> {
  public static <A extends Annotation> Optional<A> findAnnotation(Optional<? extends AnnotatedElement> element) {
      return element.<Optional<A>>map(annotatedElement -> Optional.empty()).orElseGet(() -> findAnnotation((AnnotatedElement) null));
  }

  private static <A extends Annotation> Optional<A> findAnnotation(AnnotatedElement element) {
    return Optional.empty();
  }
}