// "Add on demand static import for 'java.lang.annotation.ElementType'" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({Element<caret>Type.//simple end comment

  METHOD})
@interface F {}