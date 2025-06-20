<error descr="Module Import Declarations are not supported at language level '24'">import module java.base;</error>
import a.b.*;

class A{
  <error descr="Reference to 'List' is ambiguous, both 'a.b.List' and 'java.util.List' match">List</error> a;
}
