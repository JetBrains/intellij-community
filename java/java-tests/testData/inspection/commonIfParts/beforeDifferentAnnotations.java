// "Fix all 'Common parts of if statement branches can be extracted' problems in file" "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class Test {
  Object getX() {
    return null;
  }

  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.LOCAL_VARIABLE)
  @interface MyAnnotation1 {
  }

  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.LOCAL_VARIABLE)
  @interface MyAnnotation2 {
  }

  class Test {
    int work() {
      if<caret> (true) {
        @MyAnnotation1 Object x = null;
        return 42;
      } else {
        @MyAnnotation2 Object x = null;
        return 1;
      }
    }
  }

}