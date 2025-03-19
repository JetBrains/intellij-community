import java.io.*;

class C {
  private <warning descr="Field 'a' is marked 'transient', in non-Serializable class">transient</warning> int a = 1;
}

record R(int a) {
  static <warning descr="Field 'b' is marked 'transient', in non-Serializable class">transient</warning> int b = 1;
}