import java.lang.annotation.Annotation;

// "Iterate" "true"
class Test {
  void foo() {
      for (Annotation annotation: getClass().getAnnotations()) {
          
      }

  }
}