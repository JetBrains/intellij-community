package rrr;
import ppp.*;
import qqq.*;
public class Use { int x = new Helper().f(); }  // Helper -> qqq.Helper (ppp.Helper inaccessible, JLS 7.5.2)
