class MyUpdate {

  public static MyUpdate createUpdate(Container <caret>extensionsContainer) {
    System.out.println("extensionsContainer = " + extensionsContainer);
    return new MyUpdate();
  }

  public static void main(String[] args) {
    MyUpdate.createUpdate(Container.empty());
  }
}

class Container {
  public static Container empty() {
    return new Container();
  }
}