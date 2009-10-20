public class Test {

  static MyEnum getAvailabilityStatusCode() {

    return get<caret>
  }


  public static enum MyEnum {

    VALUE_1,
    VALUE_2
  }

}