import java.lang.Deprecated;
public class DeprecatedUsages {
  
  ///<caret> @deprecated We before java 5 there were no annotations 
  /// so that how you were expected to deprecate something 
  @Deprecated
  void onlyTag() {}
  
  /// @deprecated
  @Deprecated
  void emptyTag() {}
  
  /// @deprecated
  @Deprecated
  void emptyTagWithAnnotation() {}
  
  
  /// @deprecated Markdown Javadoc updated the meaning of the deprecated tag. 
  /// This is a bit of heresy and honestly artificial friction but so be it. 
  @Deprecated
  void tagWithAnnotation() {}
} 