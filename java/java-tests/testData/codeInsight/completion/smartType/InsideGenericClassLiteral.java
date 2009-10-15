class Foo {

  <T extends CharSequence> T bar(Class<T> t) {}

  {
     bar(St<caret>.class)
  }

  }