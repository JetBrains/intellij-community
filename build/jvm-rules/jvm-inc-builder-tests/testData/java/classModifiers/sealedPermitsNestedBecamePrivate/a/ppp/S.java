package ppp;
public sealed class S permits Outer.Nested { }  // permits clause is the only reference to Nested: ClassPermitsUsage
