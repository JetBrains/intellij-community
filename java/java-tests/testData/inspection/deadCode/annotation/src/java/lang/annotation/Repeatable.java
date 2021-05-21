package java.lang.annotation;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Repeatable {
  @SuppressWarnings("unused")
  Class<? extends Annotation> value();
}
