package qqq;
import ppp.Outer;
public class Use { int x = Outer.Nested.g(); }  // g is declared in Base: the usage owner is the qualifying type Outer$Nested, not the declaring class
