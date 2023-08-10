class Test {
  void additiveTest(int a, int b, int c, int d) {
    int e = -/*1*/(a /*2*/<caret>* b/*3*/)/*4*/;
    int f = -(a <caret>* -/*1*/b);
    int g = -(a <caret>- b);
    int h = -(-/*1*/a <caret>+ b);
    int i = a /*1*// (b /*2*/<caret>* c);
    int j = -/*1*/a / -(-b <caret>*/*2*/ c);
    int k = a / -(b <caret>/ -c /*1*/* /*2*/d);
    int l = /*1*/a /*2*/* -b / (c <caret>* d);
    int m = a/*1*/ - b / (c *<caret> -d);
    int n = a /*1*// (b *<caret> /*2*/-c / d) /*3*// a;
    int p = a - (-b <caret>- c);
    int q = a - (b +<caret> -c);
    int r = a /*1*/- (b -/*2*/ c <caret>+/*3*/ d);
    int s = -a - -(b /*1*/- c <caret>+/*2*/ -d);
    int t = a/*1*/ - (b /*2*/<caret>- c) /*3*/- d;
    int u = a -/*1*/ b /*2*/- /*3*/(c /*4*/<caret>- d);
    int v = a - (b -<caret> -c) - d;
    int w = -/*1*/(b -<caret> -c) - a;
    int x = a - (b -<caret> c/*1*/ / -/*2*/d/*3*/);
    int y = a / (b <caret>* /*1*/-/*2*/c/*3*/);
    int z = a / (-/*1*/b <caret>* c / d);
    int aa = a / (b * <caret>-/*1*/c / d);
    int bb = a / (b * <caret>c / -/*1*/d);
    int cc = /*1*/a / /*2*/(b */*3*/ <caret>-c) / -/*4*/d;
  }
}