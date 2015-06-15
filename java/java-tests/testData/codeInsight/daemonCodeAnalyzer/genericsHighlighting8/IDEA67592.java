
interface A
{
  interface B  { }
}

interface D
{
  interface B  { }
}

interface C extends A, D
{
  interface E extends <error descr="Reference to 'B' is ambiguous, both 'A.B' and 'D.B' match">B</error> {}
}