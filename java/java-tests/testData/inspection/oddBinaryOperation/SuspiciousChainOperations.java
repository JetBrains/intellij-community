
class DomainObject {
  int a;
  int b;
  int c;
  int d;
  int f;

  void copy(DomainObject other) {
    a = other.a;
    b = other.b;
    this.c = other.c;
    d = other.d;
    f = <warning descr="Reference name is the same as earlier in chain">other.d</warning>;
  }
}

class OddBinaryOperation {
  void foo(DomainObject first, DomainObject second) {
    boolean b = first.a == second.a || first.b == second.b || first.c == second.c || first.d == second.d || first.f == <warning descr="Reference name is the same as earlier in chain">second.d</warning>;
  }

  void bar(DomainObject first, DomainObject second) {
    first.a = second.a;
    first.b = second.b;
    first.c = second.c;
    first.d = <warning descr="Reference name is the same as earlier in chain">second.c</warning>;
    first.f = second.f;
  }
}

class IssueObject {
  private Object rawValue;
  private int baseValue;
  private int counterFrequency;
  private int systemFrequency;
  private long timeStamp;
  private long timeStamp100nSec;
  private long counterTimeStamp;
  private Object counterType;

  boolean myCompare(IssueObject other) {
    return
      rawValue == other.rawValue &&
      baseValue == other.counterFrequency && // <=
      counterFrequency == <warning descr="Reference name is the same as earlier in chain">other.counterFrequency</warning> && // <=
      systemFrequency == other.systemFrequency &&
      timeStamp == other.timeStamp &&
      timeStamp100nSec == other.timeStamp100nSec &&
      counterTimeStamp == other.counterTimeStamp &&
      counterType == other.counterType;
  }
}