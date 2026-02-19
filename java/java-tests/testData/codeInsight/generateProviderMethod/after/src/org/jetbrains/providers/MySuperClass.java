package org.jetbrains.providers;

public class MySuperClass {
  public static class MySubProviderImpl extends MyProviderImpl {
      public static MySubProviderImpl provider() {
          return new MySubProviderImpl(<caret>);
      }
  }
}
