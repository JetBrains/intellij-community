// "Remove annotation" "true"

import org.jetbrains.annotations.*;

class Foo {
  <caret>@NotNull
  int foo(){return 0;}
}