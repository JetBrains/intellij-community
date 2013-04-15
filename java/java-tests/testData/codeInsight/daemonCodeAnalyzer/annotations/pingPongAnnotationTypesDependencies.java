@interface A1 {
   <error descr="Cyclic annotation element type">B1</error> value();
}

@interface B1 {
   <error descr="Cyclic annotation element type">A1</error> value();
}

@interface C1 {
   A1 value();
}

@interface D1 {
    <error descr="Cyclic annotation element type">D1</error> value();
}

enum E1 {
    E_1;

    @F(E_1)
    void foo() {
    }
}

@interface F {
    E1 value();
}