package pkg;

import java.lang.annotation.*;

public class TypeAnnoNestedGeneric {
  public static class Nested<T extends Object> {
    public static TypeAnnoNestedGeneric.Nested<@TAA String> foo() { return new Nested<String>(); }
  }

  @Target(ElementType.TYPE_USE)
  @interface TAA {}
}