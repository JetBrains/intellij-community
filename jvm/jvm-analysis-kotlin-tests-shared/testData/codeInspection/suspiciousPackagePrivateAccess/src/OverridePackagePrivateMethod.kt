package xxx;

class OverridePackagePrivateMethod : PackagePrivateAbstractMethod() {

  override fun <warning descr="Method foo() overrides a package-private method from class xxx.PackagePrivateAbstractMethod which is declared in a different module 'dep'">foo</warning>() {}

  override fun <warning descr="Method bar() overrides a package-private method from class xxx.PackagePrivateAbstractMethod which is declared in a different module 'dep'">bar</warning>() {}

  override protected fun baz() {}

  override fun qux() {}
}