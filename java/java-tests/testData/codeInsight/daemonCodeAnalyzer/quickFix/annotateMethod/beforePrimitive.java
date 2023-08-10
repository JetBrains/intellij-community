// "Remove annotation" "true-preview"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@NotNull
  int foo(){return 0;}
}