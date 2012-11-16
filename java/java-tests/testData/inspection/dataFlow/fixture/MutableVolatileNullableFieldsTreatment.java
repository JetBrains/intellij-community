import org.jetbrains.annotations.Nullable;

class Foo {
  @Nullable volatile Object data;

  void checkNotNullAndUse(Foo f) {
    if (f.data != null) {
      System.out.println(<warning descr="Method invocation 'f.data.hashCode()' may produce 'java.lang.NullPointerException'">f.data.hashCode()</warning>);
    }
  }

  void checkNullAndReturn(Foo f) {
    if (f.data == null) {
      return;
    }
    System.out.println(<warning descr="Method invocation 'f.data.hashCode()' may produce 'java.lang.NullPointerException'">f.data.hashCode()</warning>);
  }

  void warnWhenWrongCheck() {
    if (data != null) {
      return;
    }
    System.out.println(<warning descr="Method invocation 'data.hashCode()' may produce 'java.lang.NullPointerException'">data.hashCode()</warning>);
    System.out.println(<warning descr="Method invocation 'data.hashCode()' may produce 'java.lang.NullPointerException'">data.hashCode()</warning>);
  }

  void warnWhenNotChecked(Foo f) {
    System.out.println(<warning descr="Method invocation 'f.data.hashCode()' may produce 'java.lang.NullPointerException'">f.data.hashCode()</warning>);
  }
  
  void warnWhenNotCheckedThis() {
    System.out.println(<warning descr="Method invocation 'data.hashCode()' may produce 'java.lang.NullPointerException'">data.hashCode()</warning>);
  }
  
  void dontWarnWhenDoubleChecked(Foo f) {
    if (f.data == null) {
      return;
    }
    if (f.data == null) {
      return;
    }
    System.out.println(<warning descr="Method invocation 'f.data.hashCode()' may produce 'java.lang.NullPointerException'">f.data.hashCode()</warning>);
  }

  void dontWarnWhenDoubleChecked_This_Synchronized() {
    if (data == null) {
      return;
    }
    synchronized (this) {
      if (data == null) {
        return;
      }
    }
    System.out.println(<warning>data.hashCode()</warning>);
  }
  
  void dontWarnWhenDoubleChecked_This_WithMethodCall() {
    if (data == null) {
      return;
    }
    System.out.println(<warning>data.hashCode()</warning>);
    if (data == null) {
      return;
    }
    System.out.println(<warning>data.hashCode()</warning>);
  }

}