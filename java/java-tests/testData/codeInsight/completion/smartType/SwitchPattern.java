class Test {

  String testSwitch() {
    BaseInterface k = new BaseInterface.Record1();
    int i2 = 0;
    return switch (<caret>)
    {
      case BaseInterface.Record1 record1 -> "1";
      case BaseInterface.Record2 record2 -> "2";
      default -> "3";
    };
  }

  sealed interface BaseInterface permits BaseInterface.Record1, BaseInterface.Record2{

    record Record1() implements BaseInterface {
    }

    record Record2() implements BaseInterface {
    }
  }
}