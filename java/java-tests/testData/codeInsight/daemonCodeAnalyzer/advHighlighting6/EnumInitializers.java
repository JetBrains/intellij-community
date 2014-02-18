import java.util.*;
public class MyEnum {

  enum Test {A,B}

  public static Test[] accepted1() {
    List<Test> list = new ArrayList<Test>();
    return list.toArray(new Test[list.size()]);
  }

  public static Test[] accepted2() {
    return new Test[]{};
  }

  Test t = <error descr="Enum types cannot be instantiated">new Test()</error>;
}