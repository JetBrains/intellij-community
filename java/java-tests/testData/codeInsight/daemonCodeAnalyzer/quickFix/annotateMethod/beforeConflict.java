// "Remove annotation" "true"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@NotNull @Nullable
  String foo(){return "";}
}