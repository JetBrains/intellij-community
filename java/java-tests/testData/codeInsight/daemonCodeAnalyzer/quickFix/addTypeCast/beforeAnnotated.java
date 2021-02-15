// "Cast parameter to 'java.lang.@Anno String'" "true"
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target(ElementType.TYPE_USE)
@interface Anno {}
class a {
 void f(@Anno String s) { }
 void m(Object o) {
   if (o instanceof String) {
     f(<caret>o);
   }
 }
}

