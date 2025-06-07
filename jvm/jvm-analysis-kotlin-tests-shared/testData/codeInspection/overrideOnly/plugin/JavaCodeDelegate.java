package plugin;

import library.JavaClass;
import library.KotlinClass;

class JavaInheritor extends JavaClass {
  JavaClass javaDelegate;

  @Override
  public void overrideOnlyMethod() {
    super.overrideOnlyMethod();
    javaDelegate.overrideOnlyMethod();
  }

  public void overrideOnlyMethod(int x) {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
  }
  public void notOverrideOnlyMethod() {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
  }
}

class KotlinInheritor extends KotlinClass {
  KotlinClass kotlinDelegate;

  @Override
  public void overrideOnlyMethod() {
    super.overrideOnlyMethod();
    kotlinDelegate.overrideOnlyMethod();
  }

  public void overrideOnlyMethod(int x) {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
  }

  public void notOverrideOnlyMethod() {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
  }
}