class T {
  void f(String[] a) {
    <warning descr="'for' without braces">for</warning> (String s : a)
      System.out.println(s);

    <warning descr="'if' without braces">if</warning> (a.length == 0)
      System.out.println("no");
    <warning descr="'else' without braces">else</warning>
      System.out.println(a.length);

    <warning descr="'for' without braces">for</warning> (int i = 0; i < a.length; i++)
      System.out.println(a[i]);

    int j = 0;
    <warning descr="'do' without braces">do</warning> System.out.println(a[j++]);
    while (j < a.length);

    int k = 0;
    <warning descr="'while' without braces">while</warning> (k < a.length)
      System.out.println(a[k++]);

    <warning descr="'if' without braces">if</warning> (a.length == 0)
      System.out.println("no");

    if (a.length == 0) {
    } <warning descr="'else' without braces">else</warning>
      System.out.println(a.length);
  }

  void ff(String[] a) {
    <warning descr="'if' without braces">if</warning> (a.length != 0)
      <warning descr="'for' without braces">for</warning> (String arg : a)
        <warning descr="'if' without braces">if</warning> (arg.length() > 1)
          <warning descr="'for' without braces">for</warning> (int i = 0; i < arg.length(); i++)
            System.out.println(arg.charAt(i));
        <warning descr="'else' without braces">else</warning> System.out.println(0);
    <warning descr="'else' without braces">else</warning> System.out.println("no");
  }

  void fff(String[] a) {
    if (a.length == 0) {
      System.out.println();
    }
    else if (a.length > 10) {
      System.out.println();
    }
  }
}