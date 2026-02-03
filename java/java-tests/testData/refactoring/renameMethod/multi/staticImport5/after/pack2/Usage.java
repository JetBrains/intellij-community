package pack2;

import pack1.A;

import static pack1.A.renamedStaticMethod;

class Usage {
    {
        A.renamedStaticMethod(27);
    }
}