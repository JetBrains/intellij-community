// "Replace with findFirst()" "true"

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class Main {
  enum MyEnum { FOO, BAR, BAZ }

  public static MyEnum find(List<EnumSet<MyEnum>> list) {
      return list.stream().flatMap(Collection::stream).filter(val -> val.name().startsWith("B")).findFirst().orElse(MyEnum.FOO);
  }
}