// "Make 'a.f' return 'java.util.List<java.lang.@N String>'" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.TYPE_USE})
@interface N {}

class a {
  List<@N String> f() {
   return;
 }
}

class b extends a {
  List<@N String> f() {
    return null;
  }
}