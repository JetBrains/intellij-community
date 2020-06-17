package jdk.internal;

import java.lang.annotation.*;

@Target({ElementType.METHOD,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
  ElementType.PACKAGE,
  ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface PreviewFeature {
  /**
   * Name of the preview feature the annotated API is associated
   * with.
   */
  public Feature feature();

  public boolean essentialAPI() default false;

  public enum Feature {
    PATTERN_MATCHING_IN_INSTANCEOF,
    TEXT_BLOCKS,
    RECORDS,
    ;
  }
}
