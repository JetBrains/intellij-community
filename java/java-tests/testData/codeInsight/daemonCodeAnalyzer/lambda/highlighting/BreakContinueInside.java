class Test {
    static interface I {
       void m();
    }

    I i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
    I i2 = ()-> { break <error descr="Undefined label: 'l'">l</error>; };
    I i3 = ()-> {
        I i_i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
        I i_i2= ()-> { break <error descr="Undefined label: 'l'">l</error>; };

        foo:
        while (true) {
            if (false) {
                break;
            }
            if (true) {
                break <error descr="Undefined label: 'l'">l</error>;
            } else {
                continue foo;
            }
            if (false) {
                break <error descr="Undefined label: 'l1'">l1</error>;
            }
        }
    };
    I i4 = ()-> { <error descr="Continue outside of loop">continue;</error> };
    I i5 = ()-> { <error descr="Break outside switch or loop">break;</error> };

    {
         l:
         while (true) {
            I i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
            I i2 = ()-> { break <error descr="Undefined label: 'l'">l</error>; };
            I i3 = ()-> {
                I i_i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
                I i_i2= ()-> { break <error descr="Undefined label: 'l'">l</error>; };
                foo:
                while (true) {
                    if (false) {
                        break;
                    }
                    if (true) {
                        break <error descr="Undefined label: 'l'">l</error>;
                    } else {
                        continue foo;
                    }
                    if (false) {
                        break <error descr="Undefined label: 'l1'">l1</error>;
                    }
                }
            };
        }


        while (true) {
            I i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
            I i2 = ()-> { break <error descr="Undefined label: 'l'">l</error>; };
            I i3 = ()-> {
                I i_i1 = ()-> { continue <error descr="Undefined label: 'l'">l</error>; };
                I i_i2= ()-> { break <error descr="Undefined label: 'l'">l</error>; };
                foo:
                while (true) {
                    if (false) {
                        break;
                    }
                    if (true) {
                        break <error descr="Undefined label: 'l'">l</error>;
                    } else {
                        continue foo;
                    }
                    if (false) {
                        break <error descr="Undefined label: 'l1'">l1</error>;
                    }
                }
            };
        }
    }
}