package xxx;

public class InnerClasses {
  static class PackagePrivateInnerClass {
  }

  protected static class ProtectedInnerClass {
  }

  static class PackagePrivateInnerClassWithConstructor {
    PackagePrivateInnerClassWithConstructor() {
    }
  }

  public static class ClassWithPackagePrivateConstructor {
    ClassWithPackagePrivateConstructor() {
    }
  }
}