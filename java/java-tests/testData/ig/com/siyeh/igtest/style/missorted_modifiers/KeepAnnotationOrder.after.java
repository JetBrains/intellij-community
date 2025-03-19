import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class KeepAnnotationOrder {

  @A
  @B
  @C
  private final String foo = "";

  @Target(ElementType.FIELD) public @interface A {}
  @Target(ElementType.FIELD) public @interface B {}
  @Target(ElementType.FIELD) public @interface C {}
}