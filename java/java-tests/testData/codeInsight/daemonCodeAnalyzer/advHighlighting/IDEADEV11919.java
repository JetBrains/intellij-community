interface Bar {
    void DoBar();
}

abstract class Foo {
    public Foo(Bar b) {
    }
}

class Inh extends Foo {
    public Integer myField = new Integer(0);

    public Inh() {
        super(new Bar() {
            public void DoBar() {
                <error descr="Cannot reference 'Inh.this' before supertype constructor has been called">Inh.this</error>.myField.toString();
            }
        });

        class E extends Foo {
            E() {
                super(new Bar() {
                    public void DoBar() {
                        Inh.this.myField.toString();
                    }
                });
            }

            public void DoBar() {
                Inh.this.myField.toString();
            }
        }
        Inh.this.myField.toString();
    }

    public Inh(Bar b) {
        super(b);
    }
}

//IDEADEV-14306
class Base {
  protected String field;

  public Base(final String field, int l) {
    this.field = field;
  }
}

class Inhertior extends Base {
  public Inhertior() {
    super("", <error descr="Cannot reference 'Base.field' before supertype constructor has been called">field</error>.length());
  }
}
//end of IDEADEV-14306