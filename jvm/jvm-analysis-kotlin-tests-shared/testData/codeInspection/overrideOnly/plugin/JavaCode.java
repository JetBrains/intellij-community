package plugin;

import java.util.function.Consumer;
import library.JavaClass;
import library.JavaInterface;
import library.KotlinClass;
import library.KotlinInterface;

import library.JavaClassOverrideOnly;
import library.JavaInterfaceOverrideOnly;
import library.JavaMembersOverrideOnly;
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
    JavaMembersOverrideOnly javeMembersOverrideOnly,
    KotlinClassOverrideOnly kotlinClassOverrideOnly,
    KotlinInterfaceOverrideOnly kotlinInterfaceOverrideOnly
  ) {
    javaClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
    kotlinClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();

    javaClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    javaClassOverrideOnly.finalMethod(); // no warning because it's a final method
    javaInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
    javeMembersOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();
    javeMembersOverrideOnly.finalMethod(); // no warning because it's a final method
    kotlinClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>();
    kotlinInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>();

    //No warning
    JavaClassOverrideOnly.staticMethod();
    JavaInterfaceOverrideOnly.staticMethod();
    JavaMembersOverrideOnly.staticMethod();
    KotlinClassOverrideOnly.staticMethod();
    KotlinInterfaceOverrideOnly.staticMethod();
  }

  public void methodReferences() {
    Consumer<JavaClass> a = JavaClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<JavaInterface> b = JavaInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
    Consumer<KotlinClass> c = KotlinClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<KotlinInterface> d = KotlinInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;

    Consumer<JavaClassOverrideOnly> a1 = JavaClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<JavaClassOverrideOnly> a2 = JavaClassOverrideOnly::finalMethod; // no warning because it's a final method
    Consumer<JavaInterfaceOverrideOnly> b1 = JavaInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
    Consumer<JavaMembersOverrideOnly> c1 = JavaMembersOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;
    Consumer<JavaMembersOverrideOnly> c2 = JavaMembersOverrideOnly::finalMethod; // no warning because it's a final method
    Consumer<KotlinClassOverrideOnly> d1 = KotlinClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>;
    Consumer<KotlinInterfaceOverrideOnly> e1 = KotlinInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>;

    //No warning
    Runnable a3 = JavaClassOverrideOnly::staticMethod;
    Runnable b2 = JavaInterfaceOverrideOnly::staticMethod;
    Runnable c3 = JavaMembersOverrideOnly::staticMethod;
    Runnable d2 = KotlinClassOverrideOnly::staticMethod;
    Runnable e2 = KotlinInterfaceOverrideOnly::staticMethod;
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
