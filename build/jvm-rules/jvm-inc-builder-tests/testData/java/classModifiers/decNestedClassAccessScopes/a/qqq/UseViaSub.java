package qqq;
import ppp.Sub;
public class UseViaSub { int x = Sub.f(); }  // subclass-qualified access: usage owner is Sub, stays legal and must NOT be recompiled
