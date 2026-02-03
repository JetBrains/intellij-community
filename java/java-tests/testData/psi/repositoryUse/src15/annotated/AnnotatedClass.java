package annotated;

import annotations.*;

@annotations.AnnotationType
public class AnnotatedClass {
   @AnnotationType("<") public void correctMethod() {}
   @wrongpkg.AnnotationType public void wrongMethod() {}
}
