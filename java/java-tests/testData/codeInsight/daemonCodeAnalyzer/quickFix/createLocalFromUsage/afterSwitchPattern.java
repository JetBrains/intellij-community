// "Create local variable 'x2'" "true-preview"

class A {
  String testPattern() {
      BaseInterface x2;
      return switch(x2){
      case BaseInterface.Record1 record1 -> "1";
      case BaseInterface.Record2 record1 -> "1";
      default -> "2";
    };
  }

  sealed interface BaseInterface permits BaseInterface.Record1, BaseInterface.Record2 {

  sealed class Record1() implements BaseInterface {
  }

  record Record2() implements BaseInterface {
  }
}
}