// "Add on-demand static import for 'java.lang.Math'" "true-preview"
package test;

import static java.lang.Math.abs;

public class C {{
  abs(1.0);
  <caret>Math.max(1, 2);
}}