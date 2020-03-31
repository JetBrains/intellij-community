package pkg;

import org.jetbrains.annotations.ApiStatus;

public @interface NonAnnotatedAnnotation {
  String nonAnnotatedAttributeInNonAnnotatedAnnotation() default "";

  @ApiStatus.Experimental String annotatedAttributeInNonAnnotatedAnnotation() default "";
}