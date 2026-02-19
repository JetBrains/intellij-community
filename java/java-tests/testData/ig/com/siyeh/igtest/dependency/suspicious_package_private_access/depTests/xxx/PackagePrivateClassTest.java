package xxx;

public class PackagePrivateClassTest extends PublicClass {
  public void test1() {
    new <warning descr="Class xxx.PackagePrivateClass is package-private and used in tests, but declared in the production source">PackagePrivateClass</warning>();
  }
  
  public void test2() {
    new AnotherPackagePrivateClass();
  }

  @Override
  public void <warning descr="Method packagePrivateMethod() in tests overrides a package-private method from class xxx.PublicClass which is declared in the production source">packagePrivateMethod</warning>(){
  }
}