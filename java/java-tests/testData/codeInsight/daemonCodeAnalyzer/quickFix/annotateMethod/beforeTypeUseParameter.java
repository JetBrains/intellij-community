// "Fix all '@NotNull/@Nullable problems' problems in file" "true"
package typeUse;

import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) public @interface NotNull { }
@Target(ElementType.TYPE_USE) public @interface Nullable { }

interface Foo {
  void processString(@NotNull String s);
  void processArray(String @NotNull [] arr);
  void processArray2(String @NotNull [] arr);
  void processArray3(String @NotNull ... arr);
  void processString2(java.lang.@NotNull String qualified);
  void processList(@NotNull List<@NotNull String> list);
  void processList2(@NotNull List<String> finalList);
}

static class Bar implements Foo {
  @Override
  public void processString(String <caret>s) { }
  
  @Override
  public void processArray(String[] arr) { }

  @Override
  public void processArray2(@NotNull String[] arr) { }

  @Override
  public void processArray3(String... arr) { }

  @Override
  public void processString2(java.lang.String qualified) { }

  @Override
  public void processList(List<@NotNull String> list) { }

  @Override
  public void processList2(final List<String> finalList) { }
}
