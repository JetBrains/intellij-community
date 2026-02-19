import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@interface M {}

@Target(ElementType.PARAMETER)
@interface P {}

@Target({ElementType.PARAMETER, ElementType.METHOD})
@interface MP {}

@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
@interface MPT {}

@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@interface MPF {}

record R1(<warning descr="Annotation has no effect: its target is METHOD but the corresponding accessor is explicitly declared">@M</warning> String s) {
  @Override
  public String s() {
    return s;
  }
}

record R2(<warning descr="Annotation has no effect: its target is PARAMETER but the canonical constructor is explicitly declared">@P</warning> String s) {
  R2(String s) {
    this.s = s;
  }
}

record R3(<warning descr="Annotation has no effect: its targets are METHOD and PARAMETER but both accessor and canonical constructor are explicitly declared">@MP</warning> String s) {
  R3(String s) {
    this.s = s;
  }

  @Override
  public String s() {
    return s;
  }
}

record R4(@MP String s) {
  R4(@MP String s) {
    this.s = s;
  }
}

record R5(@MP String s) {
  @Override
  public String s() {
    return s;
  }
}

record R6(@P String s) {
  R6 {}
}

record R7(@MPF String s) {
  R7(String s) {
    this.s = s;
  }

  @Override
  public String s() {
    return s;
  }
}

record R8(<warning descr="Annotation has no effect: its targets are METHOD and PARAMETER but both accessor and canonical constructor are explicitly declared">@MPT</warning> String s) {
  R8(String s) {
    this.s = s;
  }

  @Override
  public String s() {
    return s;
  }
}
