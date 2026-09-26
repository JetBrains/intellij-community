// "Use static import for 'java.lang.Math.abs'" "true-preview"
package test;

import static java.lang.Math.abs;

public class X {{
  Math.abs<caret>(1.0);
}}