class Dummy {

  public @interface Debug {
    String value() default "[no comment]";
  }

  private static final class Constants {
    private static final String INPUT = "Input";
  }

  <error descr="Annotations are not allowed here">@Dummy.Debug(Constants.INPUT)</error><EOLError descr="Identifier or type expected"></EOLError>
}