package org.checkerframework.checker.tainting.qual;

public class Simple {
  
    void simple() {
      String s = source();
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }
  
    void alias() {
      String s1 = source();
      String s = s1;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }
  
    void unknown() {
      String s = foo();
      sink(<warning descr="Unknown string is used as safe parameter">s</warning>);
    }
  
    void literalOnly() {
      String s = null;
      s = "safe";
      sink(s);
    }
  
    void safeCall() {
      String s = "safe";
      s = safe();
      sink(s);
    }
  
    void sourceCallToSink() {
      sink(<warning descr="Unsafe string is used as safe parameter">source()</warning>);
    }
  
    void safeCallToSink() {
      sink(safe());
    }
  
    void sourceFromClass() {
      String s = (new WithSourceParent()).source();
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void sourceFromChildClass() {
      WithSourceChild child = new WithSourceChild();
      String s = child.source();
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void withParenthesis() {
      String s1 = (source());
      s1 = (foo());
      String s = (s1);
      sink((<warning descr="Unsafe string is used as safe parameter">s</warning>));
    }
  
    @Untainted String unsafeReturn() {
      return <warning descr="Unsafe string is returned from safe method">source()</warning>;
    }
  
    void sourceToSafeString() {
      @Untainted String s = "safe";
      s = <warning descr="Unsafe string is assigned to safe variable">source()</warning>;
    }

    void unsafeConcat() {
      @Tainted String s = source();
      String s1 = "safe";
      String s2 = "safe2";
      sink(s1 + <warning descr="Unsafe string is used as safe parameter">s</warning> + s2);
    }

    void unsafeTernary(boolean b) {
      @Tainted String s = source();
      sink(b ? <warning descr="Unsafe string is used as safe parameter">s</warning> : null);
    }
  
    void fieldFromGetter() {
      String s = getField();
      sink(<warning descr="Unknown string is used as safe parameter">s</warning>);
    }
    
    void assignToSafeLocalVar() {
      String s1 = getField();
      @Untainted String safe = <warning descr="Unknown string is assigned to safe variable">s1</warning>;
      String s2 = source();
      safe = <warning descr="Unsafe string is assigned to safe variable">s2</warning>;
    }

    private final String field = foo();

    public String getField() {
      return field;
    }

    String callSource() {
      return source();
    }

    String foo() {
      return "some";
    }

    @Untainted
    String safe() {
      return "safe";
    }

    @Tainted
    String source() {
      return "tainted";
    }

    void sink(@Untainted String s1) {}

    class WithSourceParent {
      @Tainted
      String source() {
        return "tainted";
      }
    }

    class WithSourceChild extends WithSourceParent {
      @Override
      String source() {
        return super.source();
      }
    }
}