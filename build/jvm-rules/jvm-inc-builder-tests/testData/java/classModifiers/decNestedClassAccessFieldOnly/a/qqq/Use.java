package qqq;
import ppp.Outer;
public class Use { int x = Outer.Nested.value; }  // non-constant static field read: GETSTATIC records only a FieldUsage
