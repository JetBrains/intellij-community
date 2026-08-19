package ppp;
public class SamePkgUse { int x = Outer.Nested.f(); }  // same package: must stay legal and NOT be recompiled (PackageConstraint sparing)
