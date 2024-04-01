import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class NotAnnotationsInDeconstructionType {

  private void test(T<String> t) {
    t instanceof T<<error descr="Annotations are not allowed in deconstruction pattern types">@SomeAnnotation</error> String>(String s);
  }

  private void test2(T[] t) {
    if (t instanceof T <error descr="Annotations are not allowed in deconstruction pattern types">@SomeAnnotation</error> [](String s)) {
      System.out.println();
    }
  }

  record T<T>(T t) {
  }

  @Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE})
  @interface SomeAnnotation {
  }
}