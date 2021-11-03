public sealed class Hello permits Outer.Inner<caret>{
}

class Outer {
  final class Inner {}
}
