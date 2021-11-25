// "Change record component 'x' type to 'int'" "true"

interface I { int x(); }
record R(in<caret>t x) implements I {}