package plugin;

import java.util.function.Consumer;
import library.JavaClass;
import library.JavaInterface;
import library.KotlinClass;
import library.KotlinInterface;

import library.JavaClassOverrideOnly;
import library.JavaInterfaceOverrideOnly;
import library.KotlinClassOverrideOnly;
import library.KotlinInterfaceOverrideOnly;

class Invoker {
  public void invocations(
    JavaClass javaClass,
    JavaInterface javaInterface,
    KotlinClass kotlinClass,
    KotlinInterface kotlinInterface,

    JavaClassOverrideOnly javaClassOverrideOnly,
    JavaInterfaceOverrideOnly javaInterfaceOverrideOnly,
    KotlinClassOverrideOnly kotlinClassOverrideOnly,
    KotlinInterfaceOverrideOnly kotlinInterfaceOverrideOnly
  ) {
    javaClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
    kotlinClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();

    javaClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
    kotlinClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
  }

  public void methodReferences() {
    Consumer<JavaClass> a = JavaClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<JavaInterface> b = JavaInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
    Consumer<KotlinClass> c = KotlinClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<KotlinInterface> d = KotlinInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;

    Consumer<JavaClassOverrideOnly> a1 = JavaClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<JavaInterfaceOverrideOnly> b1 = JavaInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
    Consumer<KotlinClassOverrideOnly> c1 = KotlinClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<KotlinInterfaceOverrideOnly> d1 = KotlinInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
  }
}

class JavaInheritor extends JavaClass {
  //No warning
  @Override
  public void overrideOnlyMethod() {
  }
}

class JavaImplementor implements JavaInterface {
  //No warning
  @Override
  public void implementOnlyMethod() {
  }
}

class KotlinInheritor extends KotlinClass {
  //No warning
  @Override
  public void overrideOnlyMethod() {
  }
}

class KotlinImplementor implements KotlinInterface {
  //No warning
  @Override
  public void implementOnlyMethod() {
  }
}

class JavaInheritorOverrideOnly extends JavaClassOverrideOnly {
  //No warning
  @Override
  public void overrideOnlyMethod() {
  }
}

class JavaImplementorOverrideOnly implements JavaInterfaceOverrideOnly {
  //No warning
  @Override
  public void implementOnlyMethod() {
  }
}

class KotlinInheritorOverrideOnly extends KotlinClassOverrideOnly {
  //No warning
  @Override
  public void overrideOnlyMethod() {
  }
}

class KotlinImplementorOverrideOnly implements KotlinInterfaceOverrideOnly {
  //No warning
  @Override
  public void implementOnlyMethod() {
  }
}