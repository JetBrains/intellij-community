package package1;

import package2.InnerClass;

public class AnotherPublic {
   protected void foo(){}
}

class OuterClass {
  private InnerClass instance = new InnerClass();

}
