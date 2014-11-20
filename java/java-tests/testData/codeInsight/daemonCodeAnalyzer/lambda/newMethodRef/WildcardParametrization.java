
import java.util.function.Function;

class IdeaTest {
  class Test<K>{}

  public void checkAnnotationsPresent() {
    Function<Test<? extends Annotation>, Annotation> mapper = this::getAnnotation;
    Function<Test<? extends Annotation>, ? extends Annotation> mapper1 = this::getAnnotation;
  }

  public <A extends Annotation> A getAnnotation(Test<A> annotationClass) {
    return null;
  }

  static class Annotation{}


}
