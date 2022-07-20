// "Remove annotation" "true-preview"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@Nullable
  String foo(){return "";}
}