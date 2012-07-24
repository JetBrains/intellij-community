import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Z<caret>
@interface Foo {
}

@Target({ElementType.ANNOTATION_TYPE})
@interface ZMetaAnno {}

@Target({ElementType.LOCAL_VARIABLE})
@interface ZLocalAnno {}