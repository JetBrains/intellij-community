// "Fix all '@NotNull/@Nullable problems' problems in file" "true"
package typeUse;

import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) public @interface NotNull { }
@Target(ElementType.TYPE_USE) public @interface Nullable { }

interface Foo {
  @NotNull String getString();
  String @NotNull [] getArray();
  java.lang.@NotNull String getString2();
  @NotNull List<@NotNull String> getList();
  <T> @NotNull List<String> getList2();
}

class Bar implements Foo {
  @Override
  public String g<caret>etString() {
    return "";
  }

  @Override
  public String[] getArray() {
    return new String[0];
  }

  @Override
  public java.lang.String getString2() {
    return "";
  }

  @Override
  public List<String> getList() {
    return Collections.emptyList();
  }

  @Override
  public <T> List<String> getList2() {
    return Collections.emptyList();
  }
}