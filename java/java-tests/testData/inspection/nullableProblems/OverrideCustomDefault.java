package foo;

import custom.CheckForNull;

class BaseClass {
  public void foo() {
    Object nullable = getNullable();
    if (nullable != null) {
      System.out.println(nullable.toString());
    }
  }

  @CheckForNull Object getNullable() {
    return null;
  }
}

class ChildClass extends BaseClass {
  @CheckForNull
  @Override
  Object getNullable() {
    return super.getNullable();
  }
}
