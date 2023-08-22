import java.lang.annotation.Annotation;

// "Iterate over Annotation[]" "true-preview"
class Test {
  void foo() {
      for (Annotation annotation : getClass().getAnnotations()) {

      }

  }
}