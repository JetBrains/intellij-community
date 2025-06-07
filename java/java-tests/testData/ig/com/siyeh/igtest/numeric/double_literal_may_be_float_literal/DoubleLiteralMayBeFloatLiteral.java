class DoubleLiteralMayBeFloatLiteral {

  void literal() {
    System.out.println(<warning descr="'(float)1.1' can be replaced with '1.1f'">(float)1.1</warning>);
    System.out.println(<warning descr="'(float)-7.3' can be replaced with '-7.3f'">(float)-7.3</warning>);
    System.out.println(<warning descr="'(float)-(-((4.2)))' can be replaced with '-(-((4.2f)))'">(float)-(-((4.2)))</warning>);
  }

  void error() {
    int i = <error descr="Incompatible types. Found: 'float', required: 'int'">(float)6.66;</error>
  }

  Float boxed() {
    return <warning descr="'(float) 2.0' can be replaced with '2.0f'">(float) 2.0</warning>;
  }
}