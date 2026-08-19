package qqq;
import ppp.Outer;
public class Use { int x = Outer.Nested.f(); }  // out-of-package member-only client: loses access, must be recompiled
