class SQLException extends java.lang.Exception{}

interface ICompileErrorTest {
  void foo() throws IllegalStateException, Exception;
}

abstract class CompileErrorTest implements ICompileErrorTest {
  public void foo() throws Exception {
    throw new SQLException();
  }
}

class CompileErrorTestExtended extends CompileErrorTest {
  public void foo() throws Exception {
    try {
      super.foo();
    } catch (SQLException ignore) {
    }
  }
}

