// "Create property 'appOutputPath' in 'X'" "true-preview"
class X {
  void initOutputChecker() {
    getAppOutpu<caret>tPath();
  }

  protected RunContentDescriptor executeConfiguration() {
    ApplicationManager.getApplication().invokeLater(() -> {

