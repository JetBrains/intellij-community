// "Create local variable 'i'" "true-preview"

class A {
  String testPattern() {
      BaseInterface<String> i;
      return switch (i)
      {
        case BaseInterface.Record1<String> record1 -> "1";
        case BaseInterface.Record2 record2 -> "2";
        default -> "3";
      };
  }
}

sealed interface BaseInterface<T> permits BaseInterface.Record1, BaseInterface.Record2{

  record Record1<T>() implements BaseInterface<T> {
  }

  record Record2() implements BaseInterface<String> {
  }
}