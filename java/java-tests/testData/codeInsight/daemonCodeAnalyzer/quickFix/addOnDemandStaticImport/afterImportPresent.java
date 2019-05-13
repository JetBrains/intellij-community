// "Add on demand static import for 'java.lang.Math'" "true"
package test;

import static java.lang.Math.*;
import static java.lang.Math.abs;

public class C {{
  abs(1.0);
  max(1, 2);
}}