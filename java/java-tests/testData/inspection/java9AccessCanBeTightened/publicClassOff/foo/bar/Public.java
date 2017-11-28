package foo.bar;

<warning descr="Access can be package-private">public</warning> class Public {
  <warning descr="Access can be package-private">public</warning> static class Nested {}
  <warning descr="Access can be package-private">protected</warning> class Inner {}

  private static class Impl extends Public {}
  private static class Impl2 extends Public.Nested {}
  private class Impl3 extends Public.Inner {}
}