import java.util.*;
import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class WithAnn {

  {
    map((Class<? extends Annotation> ann) -> getAnnotation(ann));
    map(this::getAnnotation);
  }

  abstract <A> A getAnnotation(Class<A> annotationClass);
  abstract <R> void map(Function<Class<? extends Annotation>, ? extends R> mapper);
}

class Test {
  private void it(final Set<Class<? extends String>> set) {
    set.forEach((clazz) -> bind(clazz));
  }

  protected <T> void bind(Class<T> clazz) {}
}