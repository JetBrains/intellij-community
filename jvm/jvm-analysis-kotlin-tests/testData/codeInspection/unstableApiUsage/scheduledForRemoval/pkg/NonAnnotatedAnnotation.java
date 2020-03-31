package pkg;

import org.jetbrains.annotations.ApiStatus;

public @interface NonAnnotatedAnnotation {
  String nonAnnotatedAttributeInNonAnnotatedAnnotation() default "";

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") String annotatedAttributeInNonAnnotatedAnnotation() default "";
}