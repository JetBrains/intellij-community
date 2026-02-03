
interface X4 { Integer m(); }
interface Y4 { Number m(); }
interface Z4 extends X4, Y4 {}

interface X1 { long m(); }
interface Y1 { Number m(); }
<error descr="'m()' in 'Y1' clashes with 'm()' in 'X1'; methods have unrelated return types">interface Z1 extends X1, Y1</error> {}

interface X2 { long m(); }
interface Y2 { int m(); }
<error descr="'m()' in 'X2' clashes with 'm()' in 'Y2'; methods have unrelated return types">interface Z2 extends X2, Y2</error> {}

interface X3 { String m(); }
interface Y3 { Integer m(); }
<error descr="'m()' in 'Y3' clashes with 'm()' in 'X3'; methods have unrelated return types">interface Z3 extends X3, Y3</error> {}

