import jdk.internal.javac.PreviewFeature;
import jdk.internal.javac.PreviewFeature.Feature;
import <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.jdk.internal.javac.preview.FromPreview</error>;

class Main {
  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">requirePatternMatching</error>();
    <error descr="Text block literals are not supported at language level '9'">Main.requireTextBlocks</error>();
    <error descr="Records are not supported at language level '9'">new Main().requireRecords</error>();
    final <error descr="Text block literals are not supported at language level '9'">NotDirectlyAnnotatedMethod</error> a = new <error descr="Text block literals are not supported at language level '9'">NotDirectlyAnnotatedMethod</error>();
    <error descr="Text block literals are not supported at language level '9'">a.f</error>();
    <error descr="Text block literals are not supported at language level '9'">NotDirectlyAnnotatedMethod</error>.g();
  }

  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  static void requirePatternMatching(){}

  @PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEXT_BLOCKS)
  static void requireTextBlocks(){}

  @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.RECORDS)
  void requireRecords(){}

}

@PreviewFeature(feature=Feature.TEXT_BLOCKS)
class NotDirectlyAnnotatedMethod {
  void f(){}
  static void g(){}
}
