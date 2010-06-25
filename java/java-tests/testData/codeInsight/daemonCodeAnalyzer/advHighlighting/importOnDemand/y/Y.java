import x1.*;
import x2.*;

class Y {
  <error descr="Reference to 'X1' is ambiguous, both 'x1.X1' and 'x2.X1' match">X1</error> x1;
}