// "Replace with 'Boolean.FALSE.equals(flag)'" "true-preview"

import org.jetbrains.annotations.Nullable;

class Test {
  public boolean b;
  public boolean c;

  boolean test(@Nullable Boolean flag) {
    return c ? b && !/*a*/(/*b*/(/*c*/<caret>flag)) : c;
  }
}