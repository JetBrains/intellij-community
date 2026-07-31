package ppp;
public class Use { int x = Outer.Nested.f(); }  // same package: the simultaneous final+private addition must still trigger the unconstrained sweep
