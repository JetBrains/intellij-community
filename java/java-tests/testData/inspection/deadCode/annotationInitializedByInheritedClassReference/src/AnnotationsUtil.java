import java.lang.annotation.*;

public class AnnotationsUtil {
  @Inherited
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  private @interface PerClassLifeCycle {
  }

  @PerClassLifeCycle
  private static class BaseMetaAnnotatedTestCase {
  }

  private static class SpecializedTestCase extends BaseMetaAnnotatedTestCase {
  }

  public static void main(String[] args) {
    System.out.println(SpecializedTestCase.class);
  }
}