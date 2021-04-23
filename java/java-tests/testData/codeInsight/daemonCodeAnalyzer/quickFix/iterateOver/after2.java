import java.lang.annotation.Annotation;

// "Iterate over Annotation[]" "true"
class Test {
  void foo() {
      for (Annotation annotation : getClass().getAnnotations()) {
          
      }

  }
}