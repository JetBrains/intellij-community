package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public @interface AnnotatedAnnotation {
  String nonAnnotatedAttributeInAnnotatedAnnotation() default "";

  @ApiStatus.Experimental String annotatedAttributeInAnnotatedAnnotation() default "";
}