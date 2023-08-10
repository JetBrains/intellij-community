// "Remove annotation" "true-preview"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@NotNull @Nullable
  String foo(){return "";}
}