class Outer {
  public record PublicRecord1() {
    public PublicRecord1 {}
  }
  public record PublicRecord2() {
    protected <error descr="Canonical constructor access level cannot be stronger than the record access level ('public')">PublicRecord2</error>() {}
  }
  public record PublicRecord3() {
    <error descr="Canonical constructor access level cannot be stronger than the record access level ('public')">PublicRecord3</error>() {}
  }
  public record PublicRecord4() {
    private <error descr="Compact constructor access level cannot be stronger than the record access level ('public')">PublicRecord4</error> {}
  }
  protected record ProtectedRecord1() {
    public ProtectedRecord1 {}
  }
  protected record ProtectedRecord2() {
    protected ProtectedRecord2() {}
  }
  protected record ProtectedRecord3() {
    <error descr="Canonical constructor access level cannot be stronger than the record access level ('protected')">ProtectedRecord3</error>() {}
  }
  protected record ProtectedRecord4() {
    private <error descr="Compact constructor access level cannot be stronger than the record access level ('protected')">ProtectedRecord4</error> {}
  }
  record PackageRecord1() {
    public PackageRecord1 {}
  }
  record PackageRecord2() {
    protected PackageRecord2() {}
  }
  record PackageRecord3() {
    PackageRecord3() {}
  }
  record PackageRecord4() {
    private <error descr="Compact constructor access level cannot be stronger than the record access level ('package-private')">PackageRecord4</error> {}
  }
  private record PrivateRecord1() {
    public PrivateRecord1 {}
  }
  private record PrivateRecord2() {
    protected PrivateRecord2() {}
  }
  private record PrivateRecord3() {
    PrivateRecord3() {}
  }
  private record PrivateRecord4() {
    private PrivateRecord4 {}
  }
  
  void test() {
    record LocalRecord1() {
      public LocalRecord1 {}
    }
    record LocalRecord2() {
      protected LocalRecord2() {}
    }
    record LocalRecord3() {
      LocalRecord3() {}
    }
    record LocalRecord4() {
      private <error descr="Compact constructor access level cannot be stronger than the record access level ('package-private')">LocalRecord4</error> {}
    }
  }
}