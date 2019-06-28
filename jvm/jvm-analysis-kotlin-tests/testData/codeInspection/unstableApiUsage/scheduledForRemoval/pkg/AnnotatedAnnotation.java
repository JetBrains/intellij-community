package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.ScheduledForRemoval(inVersion = "123.456")
public @interface AnnotatedAnnotation {
  String nonAnnotatedAttributeInAnnotatedAnnotation() default "";

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") String annotatedAttributeInAnnotatedAnnotation() default "";
}