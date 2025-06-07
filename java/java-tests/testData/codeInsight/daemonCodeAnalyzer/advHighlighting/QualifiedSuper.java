class Outer {
            class Inner1 extends Outer {
              Inner1() {}
              Inner1(Outer o) {}
            }

            class Inner2 extends Inner1 {
                public Inner2(Object o) {
                    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Outer'">o</error>.super();
                }
                public Inner2(int o) {
                  Outer.this.super();
                }
                public Inner2(Outer o) {
                  o.super(Outer.this);
                }
                public Inner2(Outer o, int par) {
                  o.super(<error descr="Cannot reference 'this' before superclass constructor is called">this</error>);
                }
                public Inner2(Outer o, Object par) {
                  <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.super(o);
                }
            }

            class BadInner extends Inner1 {
              <error descr="Cannot reference 'BadInner.this' before superclass constructor is called">BadInner()</error> {}
            }
            <error descr="Cannot reference 'BadInner2.this' before superclass constructor is called">class BadInner2 extends Inner1</error> {
            }

            class s {
                void f(Object o) {
                    new s();
                    Outer.this.new s();
                }
            }
}

class Outer2 {
  class Inner {}
}
class Ext extends Outer2 {
   class ExtInner extends Inner {
       ExtInner() {
         super();
       }
   }
}
class C {
  C(int i) {

  }

  int x() {
    return 1;
  }
}
class D extends C {
  D() {
    super(<error descr="Cannot reference 'D.super' before superclass constructor is called">D.super</error>.x());
  }
}