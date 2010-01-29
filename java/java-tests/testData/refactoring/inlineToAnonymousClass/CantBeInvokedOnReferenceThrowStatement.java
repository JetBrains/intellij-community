import java.lang.Exception;

public class Simple extends Exception{}

class Usage {
  void foo() throws Simple {
    throw new Si<caret>mple();
  }
}