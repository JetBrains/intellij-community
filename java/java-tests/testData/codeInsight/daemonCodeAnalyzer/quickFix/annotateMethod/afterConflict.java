// "Remove annotation" "true"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@Nullable
  String foo(){return "";}
}