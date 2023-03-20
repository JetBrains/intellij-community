// "Change record component 'x' type to 'int'" "true-preview"

interface I { int x(); }
record R(flo<caret>at x) implements I {}