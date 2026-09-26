package qqq;
import ppp.Outer;
public class Use { int x = Outer.Nested.f(); }  // not a subclass of Outer: loses access to protected Nested
