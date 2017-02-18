// "Replace with findFirst()" "true"

import java.util.EnumSet;
import java.util.List;

public class Main {
  enum MyEnum { FOO, BAR, BAZ }

  public static MyEnum find(List<EnumSet<MyEnum>> list) {
    for (EnumSet<MyEnum> set : lis<caret>t) {
      for (MyEnum val : set) {
        if (val.name().startsWith("B")) {
          return val;
        }
      }
    }
    return MyEnum.FOO;
  }
}