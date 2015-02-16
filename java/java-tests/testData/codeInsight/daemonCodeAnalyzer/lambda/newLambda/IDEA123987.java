import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

abstract class FooBar<T> {
  public static void checkAnnotationsPresent(final FooBar<? extends Annotation> stream) {
    stream.collect (  toSet());
  }

  abstract <R> void collect(Collector<? super T, ?, R> collector);
  static <SET> Collector<SET, ?, Set<SET>> toSet() {
    return null;
  }
}

class Boo {
  public static Set<Annotation> checkAnnotationsPresent(final Class<?> klass,
                                                        final Collection<Class<? extends Annotation>> wanted) {

    return wanted.stream()
      .filter(collectAnnotationTypes(klass)::contains)
      .map(ann -> klass.getAnnotation(ann))
      .collect(Collectors.toSet());
  }

  public static Set<Annotation> checkAnnotationsPresent1(final Class<?> klass,
                                                         final Collection<Class<? extends Annotation>> wanted) {

    return wanted.stream()
      .filter(collectAnnotationTypes(klass)::contains)
      .map(klass::getAnnotation)
      .collect(Collectors.toSet());
  }

  public static Set<Class<? extends Annotation>> collectAnnotationTypes(final Class<?> klass) {
    return Arrays.asList(klass.getAnnotations()).stream()
      .map(Annotation::annotationType)
      .collect(Collectors.toSet());
  }
}

class Foor<P> {
  public void checkAnnotationsPresent() {
    Function<Foor<? extends Annotation>, Annotation> mapper = this::getAnnotation;
  }

  public <A extends Annotation> A getAnnotation(Foor<A> annotationClass) {
    return null;
  }

  static class Annotation{}
}

abstract class FooBoo {
  {
    map((Class<? extends Annotation> ann) -> getAnnotation(ann));
    map(this::getAnnotation);
  }

  abstract <A> A getAnnotation(Class<A> annotationClass);
  abstract <R> void map(Function<Class<? extends Annotation>, ? extends R> mapper);
}