// "Replace with 'Boolean.FALSE.equals(flag)'" "true-preview"

import org.jetbrains.annotations.Nullable;

class Test {
  public boolean b;
  public boolean c;

  boolean test(@Nullable Boolean flag) {
      /*a*/
      /*b*/
      /*c*/
      return c ? b && Boolean.FALSE.equals(flag) : c;
  }
}