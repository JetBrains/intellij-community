package xxx;

public class AccessingPackagePrivateInSignatures implements Runnable {
  @Override
  public void run() {
    System.out.println(PackagePrivateInSignatures.staticField);
    System.out.println(PackagePrivateInSignatures.staticField.<warning descr="Interface xxx.PackagePrivateInterface is package-private, but declared in a different module 'dep'">eval</warning>());

    PackagePrivateInSignatures foo = new PackagePrivateInSignatures();
    foo.inParam(null);
    foo.inParam(foo.inReturnType());
    foo.inParam(<warning descr="Interface xxx.PackagePrivateInterface is package-private, but declared in a different module 'dep'">() -> "hello"</warning>);
    foo.inParam(<warning descr="Interface xxx.PackagePrivateInterface is package-private, but declared in a different module 'dep'">AccessingPackagePrivateInSignatures::myMethod</warning>);

    System.out.println(foo.instanceField);
    System.out.println(foo.instanceField.<warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">publicField</warning>);

    PublicInheritor inheritor = new PublicInheritor();
    inheritor.publicMethod();
    System.out.println(inheritor.publicField);
  }

  public static String myMethod() {
    return "";
  }
}