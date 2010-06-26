import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE, <error descr="Repeated annotation target">ElementType.ANNOTATION_TYPE</error>})
@interface A {}
