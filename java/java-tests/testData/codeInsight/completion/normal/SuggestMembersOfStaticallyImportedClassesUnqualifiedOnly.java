import foo.Foo;
import static foo.Foo.foo;

class Bar {{
  foo();
  new Foo().ba<caret>z
}}