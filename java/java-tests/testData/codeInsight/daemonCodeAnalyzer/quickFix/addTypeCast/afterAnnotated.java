// "Cast argument to 'String'" "true-preview"
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target(ElementType.TYPE_USE)
@interface Anno {}
class a {
 void f(@Anno String s) { }
 void m(Object o) {
   if (o instanceof String) {
     f((String) o);
   }
 }
}

