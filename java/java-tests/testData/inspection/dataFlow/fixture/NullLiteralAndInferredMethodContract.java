class NullLiteralAndInferredMethodContract {
  {
    initExpressionConstraints(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }

  public void initExpressionConstraints(Object parent) {
    String currentProperties = getCurrentProperties(parent);
    System.out.println(currentProperties != null ? currentProperties : "");
  }

  private String getCurrentProperties(Object parent) {
    if (parent instanceof String) {
      return  ((String) parent).substring(1);
    }
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }
}