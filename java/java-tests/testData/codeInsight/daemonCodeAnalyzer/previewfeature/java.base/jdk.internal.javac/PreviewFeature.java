package jdk.internal.javac;

import java.lang.annotation.*;

@Target({ElementType.METHOD,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
  ElementType.PACKAGE,
  ElementType.MODULE,
  ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface PreviewFeature {
  public Feature feature();

  public boolean reflective() default false;

  public enum Feature {
    TEXT_BLOCKS,
    RECORDS,
    SEALED_CLASSES,
    ;
  }
}
