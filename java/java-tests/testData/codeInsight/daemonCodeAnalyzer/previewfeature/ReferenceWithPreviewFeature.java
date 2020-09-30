import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;

class Main {
  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  static class InstanceOf{
    static void f(){}
  }
  @PreviewFeature(feature=Feature.RECORDS)
  static class Records{
    static void f(){}
  }
  @PreviewFeature(feature=Feature.TEXT_BLOCKS)
  static class TextBlocks{
    static void f(){}
  }
  static class Empty{
    static void f(){}
  }

  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">InstanceOf</error>.f();
    <error descr="Records are not supported at language level '9'">Records</error>.f();
    <error descr="Text block literals are not supported at language level '9'">TextBlocks</error>.f();
    Empty.f();
  }
}
