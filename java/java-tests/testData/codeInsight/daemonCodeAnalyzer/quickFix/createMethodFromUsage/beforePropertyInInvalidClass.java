// "Create property 'appOutputPath' in 'X'" "true"
class X {
  void initOutputChecker() {
    getAppOutpu<caret>tPath();
  }

  protected RunContentDescriptor executeConfiguration() {
    ApplicationManager.getApplication().invokeLater(() -> {

