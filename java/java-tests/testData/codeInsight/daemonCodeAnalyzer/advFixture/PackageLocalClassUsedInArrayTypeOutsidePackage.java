package b;
class C {
  {
    int u = a.B.getAs().<error descr="Cannot access 'length' in '_Dummy_.__Array__'">length</error>;
    a.B.getAs().<error descr="Cannot access 'clone()' in '_Dummy_.__Array__'">clone</error>();
  }
}