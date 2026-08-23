class AbstractMoveInstanceMethod {
  public String code;

  protected void <caret>setupApplication(Application customApplication, String code, Integer properties) {
    customApplication.setCode(code);
    customApplication.setProperties(properties);
    customApplication.start();
  }
}

class MoveInstanceMethod extends AbstractMoveInstanceMethod {
  void setup(Application application, Integer properties) {
    setupApplication(application, code, properties);
  }
}

abstract class Application {
  abstract void setCode(String code);
  abstract void setProperties(Integer properties);
  abstract void start();
}