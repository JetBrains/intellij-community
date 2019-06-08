package xxx

import xxx.StaticMembers.*

/**
 * @see PackagePrivateClass
 * @see PublicClass.packagePrivateField
 */
@Suppress("UNUSED_VARIABLE")
class AccessingPackagePrivateMembers {
  private val property = <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>()

  fun main() {
    <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>()
    var variable: <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>
    val sam = <warning descr="Interface xxx.PackagePrivateInterface is package-private, but declared in a different module 'dep'">PackagePrivateInterface</warning> { "" }

    val aClass: PublicClass = PublicClass(1);
    val aClass2: PublicClassWithDefaultConstructor = PublicClassWithDefaultConstructor();
    <warning descr="Constructor PublicClass.PublicClass() is package-private, but declared in a different module 'dep'">PublicClass</warning>()
    <warning descr="Constructor PublicClass.PublicClass(boolean) is package-private, but declared in a different module 'dep'">PublicClass</warning>(true)

    System.out.println(aClass.publicField)
    System.out.println(aClass.<warning descr="Field PublicClass.packagePrivateField is package-private, but declared in a different module 'dep'">packagePrivateField</warning>)
    System.out.println(PublicClass.PUBLIC_STATIC_FIELD)
    System.out.println(PublicClass.<warning descr="Field PublicClass.PACKAGE_PRIVATE_STATIC_FIELD is package-private, but declared in a different module 'dep'">PACKAGE_PRIVATE_STATIC_FIELD</warning>)

    aClass.publicMethod()
    aClass.<warning descr="Method PublicClass.packagePrivateMethod() is package-private, but declared in a different module 'dep'">packagePrivateMethod</warning>()

    System.out.println(<warning descr="Field StaticMembers.IMPORTED_FIELD is package-private, but declared in a different module 'dep'">IMPORTED_FIELD</warning>)
    <warning descr="Method StaticMembers.importedMethod() is package-private, but declared in a different module 'dep'">importedMethod</warning>()

    InnerClasses.<warning descr="Class xxx.InnerClasses.PackagePrivateInnerClass is package-private, but declared in a different module 'dep'">PackagePrivateInnerClass</warning>()
    InnerClasses.<warning descr="Class xxx.InnerClasses.PackagePrivateInnerClassWithConstructor is package-private, but declared in a different module 'dep'"><warning descr="Constructor PackagePrivateInnerClassWithConstructor.PackagePrivateInnerClassWithConstructor() is package-private, but declared in a different module 'dep'">PackagePrivateInnerClassWithConstructor</warning></warning>()
    InnerClasses.<warning descr="Constructor ClassWithPackagePrivateConstructor.ClassWithPackagePrivateConstructor() is package-private, but declared in a different module 'dep'">ClassWithPackagePrivateConstructor</warning>()
  }

  companion object {
    private val staticProperty = <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>()
  }
}