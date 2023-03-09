public final class SomeClass {

  public static final String MESSAGE_1 = "message 1";

  enum SomeEnum {

    AN_INSTANCE("1", AN_I<caret>MESSAGE_1);

    private final String code;
    private final String msg;

    SomeEnum(String code, String msg) {
      this.code = code;
      this.msg = msg;
    }
  }
}
