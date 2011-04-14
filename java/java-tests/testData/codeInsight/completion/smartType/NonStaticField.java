public class SmartCompletionTest {
  interface SmartCompletionType {}
  
  private SmartCompletionType myNonStaticField;
  
  private static void staticMethod(SmartCompletionType type) {}
  
  private static class StaticInnerClass extends SmartCompletionTest {
    
    private void method() {
      staticMethod(myNon<caret>);
    }
    
  }
}