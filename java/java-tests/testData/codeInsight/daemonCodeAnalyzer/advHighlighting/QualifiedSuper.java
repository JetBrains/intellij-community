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
                  o.super(<error descr="Cannot reference 'this' before supertype constructor has been called">this</error>);
                }
                public Inner2(Outer o, Object par) {
                  <error descr="Cannot reference 'this' before supertype constructor has been called">this</error>.super(o);
                }
            }

            class BadInner extends Inner1 {
              <error descr="Cannot reference 'BadInner.this' before supertype constructor has been called">BadInner()</error> {}
            }
            <error descr="Cannot reference 'BadInner2.this' before supertype constructor has been called">class BadInner2 extends Inner1</error> {
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