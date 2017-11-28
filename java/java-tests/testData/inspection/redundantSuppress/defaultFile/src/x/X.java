package x;

class S {
    public void f() {
        //noinspection HardCodedStringLiteral
        String s=null;
        //noinspection unused, HardCodedStringLiteral
        String s2="sssssss";
    }
    @SuppressWarnings({"HardCodedStringLiteral"})
    void g() {
        String s = null;
    }
    @SuppressWarnings({"HardCodedStringLiteral"})
    void g2() {
        String s = "sssssss";
    }

    void h() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        String s = null;
    }
    void h2() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        String s = "sssssss";
    }

    void i() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        class ss {
          String s = null;
        }
    }
    void i2() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        class ss {
          String s = "sssssss";
        }
    }

    /** @noinspection HardCodedStringLiteral */
    void j() {
        String s = null;
    }
    /** @noinspection HardCodedStringLiteral */
    void j2() {
        String s = "sssssss";
    }

    void k() {
        class ss {
          /** @noinspection HardCodedStringLiteral */
          String s = null;
        }
    }
    void k2() {
        class ss {
          /** @noinspection HardCodedStringLiteral */
          String s = "sssssss";
        }
    }

    @SuppressWarnings({"EmptyMethod"})
    void foo() {}

    @SuppressWarnings({"EmptyMethod"})
    void foo1() {String f;}

}
